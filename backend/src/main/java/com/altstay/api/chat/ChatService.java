package com.altstay.api.chat;

import com.altstay.api.chat.dto.ChatRequest;
import com.altstay.api.chat.dto.ChatResponse;
import com.altstay.api.chat.dto.ChatTurn;
import com.altstay.api.chat.dto.Role;
import com.altstay.api.chat.dto.TokenUsage;
import com.altstay.api.common.ModelUnavailableException;
import com.altstay.api.config.ConciergeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatService {

    private static final long MODEL_TIMEOUT_SECONDS = 30;

    private final ChatClient chatClient;
    private final ConciergePromptFactory promptFactory;
    private final ConciergeProperties properties;

    public ChatResponse answer(ChatRequest request) {
        String correlationId = UUID.randomUUID().toString().substring(0, 8);

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

        long startTime = System.currentTimeMillis();
        org.springframework.ai.chat.model.ChatResponse modelResponse;

        try {
            CompletableFuture<org.springframework.ai.chat.model.ChatResponse> future =
                    CompletableFuture.supplyAsync(() -> chatClient.prompt(new Prompt(messages)).call().chatResponse());
            modelResponse = future.get(MODEL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            log.error("Model invocation timed out after {}s: correlationId={}", MODEL_TIMEOUT_SECONDS, correlationId);
            throw new ModelUnavailableException("AI model request timed out after " + MODEL_TIMEOUT_SECONDS + " seconds", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            log.error("Model invocation failed: correlationId={}", correlationId, cause);
            throw new ModelUnavailableException("AI model is currently unavailable", cause);
        } catch (Exception ex) {
            log.error("Model invocation error: correlationId={}", correlationId, ex);
            throw new ModelUnavailableException("AI model is currently unavailable", ex);
        }

        long latencyMs = System.currentTimeMillis() - startTime;

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

        return new ChatResponse(cleanedReply, escalated, model, tokenUsage, latencyMs);
    }
}
