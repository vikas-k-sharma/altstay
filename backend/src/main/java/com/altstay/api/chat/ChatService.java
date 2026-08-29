package com.altstay.api.chat;

import com.altstay.api.auth.TenantUserDetails;
import com.altstay.api.chat.dto.ChatRequest;
import com.altstay.api.chat.dto.ChatResponse;
import com.altstay.api.chat.dto.ChatTurn;
import com.altstay.api.chat.dto.Role;
import com.altstay.api.chat.dto.TokenUsage;
import com.altstay.api.common.ModelRateLimitedException;
import com.altstay.api.common.ModelUnavailableException;
import com.altstay.api.config.ConciergeProperties;
import com.altstay.api.conversation.ConversationPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

@Service
@Slf4j
public class ChatService {

    private final ChatClient chatClient;
    private final ConciergePromptFactory promptFactory;
    private final ConciergeProperties properties;
    private final Optional<ConversationPersistenceService> conversationPersistenceService;

    public ChatService(ChatClient chatClient, ConciergePromptFactory promptFactory, ConciergeProperties properties) {
        this(chatClient, promptFactory, properties, Optional.empty());
    }

    /**
     * {@code Optional<ConversationPersistenceService>} is a genuine absence, not scaffolding: the
     * service is {@code @ConditionalOnProperty("spring.datasource.url")} and the offline suite runs
     * with no datasource at all.
     */
    @org.springframework.beans.factory.annotation.Autowired
    public ChatService(
            ChatClient chatClient,
            ConciergePromptFactory promptFactory,
            ConciergeProperties properties,
            Optional<ConversationPersistenceService> conversationPersistenceService) {
        this.chatClient = chatClient;
        this.promptFactory = promptFactory;
        this.properties = properties;
        this.conversationPersistenceService = conversationPersistenceService;
    }

    public ChatResponse answer(ChatRequest request) {
        String correlationId = MDC.get("correlationId");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString().substring(0, 8);
        }

        List<ChatTurn> history = request.history();
        int maxTurns = properties.maxHistoryTurns();
        if (history != null && history.size() > maxTurns) {
            history = history.subList(history.size() - maxTurns, history.size());
        }

        SystemMessage systemMessage = promptFactory.createSystemMessage(request.propertyName(), request.knowledgeBase());

        List<Message> messages = new ArrayList<>();
        messages.add(systemMessage);

        if (history != null) {
            for (ChatTurn turn : history) {
                if (turn.role() == Role.USER) {
                    messages.add(new UserMessage(turn.content()));
                } else if (turn.role() == Role.ASSISTANT) {
                    messages.add(new AssistantMessage(turn.content()));
                }
            }
        }

        messages.add(new UserMessage(request.message()));

        long startNanos = System.nanoTime();
        org.springframework.ai.chat.model.ChatResponse modelResponse;

        try {
            modelResponse = chatClient.prompt(new Prompt(messages)).call().chatResponse();
        } catch (Exception ex) {
            // Log the ELAPSED time, never the configured budget. Logging the configured value hid a real
            // defect for an entire phase: the log read "timed out after 2s" while 20s had actually passed.
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
            if (isTimeout(ex)) {
                log.error("Model invocation timed out: correlationId={}, elapsedMs={}, configuredReadTimeoutMs={}",
                        correlationId, elapsedMs, properties.modelReadTimeout().toMillis());
                throw new ModelUnavailableException(
                        "AI model request timed out after " + properties.modelReadTimeout().toSeconds() + " seconds", ex);
            }
            // The cause CLASS, never its message. An upstream error message is not ours and can
            // quote the request back at us - a Google API INVALID_ARGUMENT echoes part of what was
            // sent, and a driver-level failure carries "Detail: Failing row contains (...)". Either
            // one puts a guest message into the log, and §3's rule against that is absolute.
            // The message is still read by isRateLimited/isTimeout to classify; it is not logged.
            if (isRateLimited(ex)) {
                log.error("Model rate limited / quota exhausted: correlationId={}, elapsedMs={}, cause={}",
                        correlationId, elapsedMs, ex.getClass().getName());
                throw new ModelRateLimitedException("The upstream AI model is rate limited or quota exhausted", ex);
            }
            log.error("Model invocation failed: correlationId={}, elapsedMs={}, cause={}",
                    correlationId, elapsedMs, ex.getClass().getName());
            throw new ModelUnavailableException("AI model is currently unavailable", ex);
        }

        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;

        if (modelResponse == null || modelResponse.getResult() == null || modelResponse.getResult().getOutput() == null) {
            log.error("Empty response from AI model: correlationId={}", correlationId);
            throw new ModelUnavailableException("Empty response from AI model");
        }

        String rawReply = modelResponse.getResult().getOutput().getText();
        if (rawReply == null) {
            rawReply = "";
        }

        String escalationToken = promptFactory.getEscalationToken();
        boolean escalated = rawReply.contains(escalationToken);
        String cleanedReply = rawReply.replace(escalationToken, "").trim();

        ChatResponseMetadata metadata = modelResponse.getMetadata();
        String model = metadata != null && metadata.getModel() != null ? metadata.getModel() : "unknown";

        Usage usage = metadata != null ? metadata.getUsage() : null;
        int promptTokens = usage != null && usage.getPromptTokens() != null ? usage.getPromptTokens().intValue() : 0;
        int completionTokens = usage != null && usage.getCompletionTokens() != null ? usage.getCompletionTokens().intValue() : 0;
        int totalTokens = usage != null && usage.getTotalTokens() != null ? usage.getTotalTokens().intValue() : (promptTokens + completionTokens);

        TokenUsage tokenUsage = new TokenUsage(promptTokens, completionTokens, totalTokens);

        log.info("Chat call completed: correlationId={}, model={}, promptTokens={}, completionTokens={}, totalTokens={}, latencyMs={}, escalated={}",
                correlationId, model, tokenUsage.promptTokens(), tokenUsage.completionTokens(), tokenUsage.totalTokens(), latencyMs, escalated);

        ChatResponse response = new ChatResponse(cleanedReply, escalated, model, tokenUsage, latencyMs);

        // Persist turns only for authenticated, property-scoped calls
        // CurrentTenantHolder alone, for the same reason ConversationPersistenceService uses it
        // alone: TenantContextFilter populates it from the authenticated principal, so asking the
        // SecurityContext separately would only create a second path that could disagree with it.
        boolean hasTenant = com.altstay.api.tenancy.CurrentTenantHolder.get().isPresent();
        if (hasTenant && request.propertyId() != null) {
            conversationPersistenceService.ifPresent(service -> {
                try {
                    service.persistTurns(request.propertyId(), request.conversationId(), request.message(), response);
                } catch (Exception ex) {
                    // Status and cause class ONLY - never the throwable and never its message.
                    // A Postgres constraint violation carries "Detail: Failing row contains (...)",
                    // which is the guest's message; logging the stack trace would put it in the log
                    // and §3's rule against that is absolute.
                    log.error("Failed to persist conversation turns: propertyId={}, cause={}",
                            request.propertyId(), ex.getClass().getName());
                }
            });
        }

        return response;
    }

    private boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException ||
                current instanceof TimeoutException ||
                current instanceof InterruptedIOException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && (message.toLowerCase().contains("timeout") || message.toLowerCase().contains("timed out"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isRateLimited(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String className = current.getClass().getName();
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("429") ||
                    lower.contains("resource_exhausted") ||
                    lower.contains("too many requests") ||
                    lower.contains("quota exceeded") ||
                    lower.contains("rate limit")) {
                    return true;
                }
            }
            if (className.contains("TooManyRequests") || className.contains("ResourceExhausted")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
