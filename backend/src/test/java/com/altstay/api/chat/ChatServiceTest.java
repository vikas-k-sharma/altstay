package com.altstay.api.chat;

import com.altstay.api.chat.dto.ChatRequest;
import com.altstay.api.chat.dto.ChatResponse;
import com.altstay.api.chat.dto.ChatTurn;
import com.altstay.api.chat.dto.Role;
import com.altstay.api.common.ModelUnavailableException;
import com.altstay.api.config.ConciergeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ByteArrayResource;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    private ConciergeProperties properties;
    private ConciergePromptFactory promptFactory;
    private ChatService chatService;

    private static final String TEMPLATE_CONTENT = """
            You are the front-desk receptionist for {propertyName}.
            Escalate to {escalationContact} with token {escalationToken}.
            PROPERTY KNOWLEDGE BASE
            ---
            {knowledgeBase}
            ---
            """;

    @BeforeEach
    void setUp() {
        properties = new ConciergeProperties(3, 1000, 20000, "property manager", "AltStay Hostel", Duration.ofSeconds(5), Duration.ofSeconds(20));
        ByteArrayResource resource = new ByteArrayResource(TEMPLATE_CONTENT.getBytes(StandardCharsets.UTF_8));
        promptFactory = new ConciergePromptFactory(resource, properties);
        chatService = new ChatService(chatClient, promptFactory, properties);
    }

    private void mockChatClientResponse(String outputText, String modelName, int promptTokens, int completionTokens) {
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);

        AssistantMessage assistantMessage = new AssistantMessage(outputText);
        Generation generation = new Generation(assistantMessage);

        Usage usage = new DefaultUsage(promptTokens, completionTokens);
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .model(modelName)
                .usage(usage)
                .build();

        org.springframework.ai.chat.model.ChatResponse aiChatResponse = new org.springframework.ai.chat.model.ChatResponse(List.of(generation), metadata);
        when(callResponseSpec.chatResponse()).thenReturn(aiChatResponse);
    }

    @Test
    @DisplayName("History longer than maxHistoryTurns is truncated to the newest turns")
    void historyTruncationKeepsNewestTurns() {
        mockChatClientResponse("Yes, we have lockers.", "gemini-2.5-flash", 100, 20);

        List<ChatTurn> fiveTurns = List.of(
                new ChatTurn(Role.USER, "turn 1"),
                new ChatTurn(Role.ASSISTANT, "turn 2"),
                new ChatTurn(Role.USER, "turn 3"),
                new ChatTurn(Role.ASSISTANT, "turn 4"),
                new ChatTurn(Role.USER, "turn 5")
        );

        ChatRequest request = new ChatRequest("Dorm beds are 650.", fiveTurns, "do you have lockers?");
        chatService.answer(request);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatClient).prompt(promptCaptor.capture());

        List<Message> messages = promptCaptor.getValue().getInstructions();
        // Expected: 1 SystemMessage + 3 truncated history messages (turns 3, 4, 5) + 1 current UserMessage = 5 messages
        assertThat(messages).hasSize(5);
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(messages.get(1).getText()).isEqualTo("turn 3");
        assertThat(messages.get(2).getText()).isEqualTo("turn 4");
        assertThat(messages.get(3).getText()).isEqualTo("turn 5");
        assertThat(messages.get(4).getText()).isEqualTo("do you have lockers?");
    }

    @Test
    @DisplayName("Empty or null history produces a two-message prompt (SystemMessage + UserMessage)")
    void emptyHistoryProducesTwoMessages() {
        mockChatClientResponse("Check-in is 2 PM.", "gemini-2.5-flash", 50, 10);

        ChatRequest request = new ChatRequest("Check-in is 2 PM.", List.of(), "when is check-in?");
        chatService.answer(request);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatClient).prompt(promptCaptor.capture());

        List<Message> messages = promptCaptor.getValue().getInstructions();
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(1).getText()).isEqualTo("when is check-in?");
    }

    @Test
    @DisplayName("Rendered system prompt includes the knowledge base verbatim")
    void systemPromptIncludesKnowledgeBaseVerbatim() {
        mockChatClientResponse("Dorm beds are 650.", "gemini-2.5-flash", 50, 10);

        String customKb = "SPECIAL_KB_KEYWORD: 10% discount on Tuesday.";
        ChatRequest request = new ChatRequest("AltStay Hostel", customKb, List.of(), "any discounts?");
        chatService.answer(request);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatClient).prompt(promptCaptor.capture());

        Message systemMessage = promptCaptor.getValue().getInstructions().get(0);
        assertThat(systemMessage.getText()).contains(customKb);
        assertThat(systemMessage.getText()).contains("AltStay Hostel");
        assertThat(systemMessage.getText()).contains("property manager");
    }

    @Test
    @DisplayName("Custom propertyName in request overrides default property name in system prompt")
    void customPropertyNameOverridesDefault() {
        mockChatClientResponse("Yes, we have a pool.", "gemini-2.5-flash", 50, 10);

        ChatRequest request = new ChatRequest("Zostel Goa", "Pool is open 8 AM.", List.of(), "pool hours?");
        chatService.answer(request);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatClient).prompt(promptCaptor.capture());

        Message systemMessage = promptCaptor.getValue().getInstructions().get(0);
        assertThat(systemMessage.getText()).contains("Zostel Goa");
    }

    @Test
    @DisplayName("Escalation token in reply is detected, stripped, and escalated is set to true")
    void escalationTokenDetectedAndStripped() {
        String rawReply = "I am not sure about that. Let me check with the team.\n" + ConciergePromptFactory.ESCALATION_TOKEN;
        mockChatClientResponse(rawReply, "gemini-2.5-flash", 120, 25);

        ChatRequest request = new ChatRequest("Check-in is 2 PM.", List.of(), "can I park my boat?");
        ChatResponse response = chatService.answer(request);

        assertThat(response.escalated()).isTrue();
        assertThat(response.reply()).isEqualTo("I am not sure about that. Let me check with the team.");
        assertThat(response.reply()).doesNotContain(ConciergePromptFactory.ESCALATION_TOKEN);
        assertThat(response.model()).isEqualTo("gemini-2.5-flash");
        assertThat(response.usage().promptTokens()).isEqualTo(120);
        assertThat(response.usage().completionTokens()).isEqualTo(25);
        assertThat(response.usage().totalTokens()).isEqualTo(145);
        assertThat(response.latencyMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("Standard reply without escalation token sets escalated to false")
    void nonEscalatedReplySetsEscalatedFalse() {
        mockChatClientResponse("Check-in starts at 2 PM daily.", "gemini-2.5-flash", 80, 15);

        ChatRequest request = new ChatRequest("Check-in starts at 2 PM daily.", List.of(), "check-in time?");
        ChatResponse response = chatService.answer(request);

        assertThat(response.escalated()).isFalse();
        assertThat(response.reply()).isEqualTo("Check-in starts at 2 PM daily.");
    }

    @Test
    @DisplayName("Upstream model failure surfaces as ModelUnavailableException")
    void modelFailureThrowsModelUnavailableException() {
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenThrow(new RuntimeException("Google API 503 Unavailable"));

        ChatRequest request = new ChatRequest("Check-in is 2 PM.", List.of(), "hello");

        assertThrows(ModelUnavailableException.class, () -> chatService.answer(request));
    }

    @Test
    @DisplayName("Upstream model 429/quota exhaustion surfaces as ModelRateLimitedException")
    void modelRateLimitThrowsModelRateLimitedException() {
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenThrow(new RuntimeException("RESOURCE_EXHAUSTED: 429 quota exceeded for model"));

        ChatRequest request = new ChatRequest("Check-in is 2 PM.", List.of(), "hello");

        com.altstay.api.common.ModelRateLimitedException ex =
                assertThrows(com.altstay.api.common.ModelRateLimitedException.class, () -> chatService.answer(request));
        assertThat(ex.getMessage()).contains("rate limited or quota exhausted");
    }

    @Test
    @DisplayName("Authenticated property-scoped call invokes conversation persistence service")
    void authenticatedPropertyScopedCall_persistsConversation() {
        mockChatClientResponse("Check-in is 2 PM.", "gemini-2.5-flash", 80, 15);
        com.altstay.api.conversation.ConversationPersistenceService persistenceService =
                org.mockito.Mockito.mock(com.altstay.api.conversation.ConversationPersistenceService.class);
        chatService = new ChatService(chatClient, promptFactory, properties, java.util.Optional.of(persistenceService));

        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        com.altstay.api.auth.TenantUserDetails principal = new com.altstay.api.auth.TenantUserDetails(
                userId, tenantId, "hostel-slug", "staff@hostel.com", "hash", "Staff Member", true,
                java.util.Set.of("FRONT_DESK")
        );
        org.springframework.security.core.Authentication auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

        try {
            // Bind the tenant the way production does. TenantContextFilter is what writes
            // CurrentTenantHolder from the authenticated principal, and ChatService reads only that
            // holder - deliberately, so there is exactly one path from a principal to a tenant id.
            // A test that sets the SecurityContext alone would be asserting a path that does not
            // exist outside the test.
            com.altstay.api.tenancy.TenantContextTestSupport.runAs(tenantId, () -> {
                ChatRequest request = new ChatRequest("AltStay Property", "KB", List.of(), "check-in?", propertyId, conversationId);
                chatService.answer(request);
            });

            verify(persistenceService).persistTurns(
                    org.mockito.ArgumentMatchers.eq(propertyId),
                    org.mockito.ArgumentMatchers.eq(conversationId),
                    org.mockito.ArgumentMatchers.eq("check-in?"),
                    any(ChatResponse.class)
            );
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("Anonymous call does NOT invoke conversation persistence service")
    void anonymousCall_doesNotPersistConversation() {
        mockChatClientResponse("Check-in is 2 PM.", "gemini-2.5-flash", 80, 15);
        com.altstay.api.conversation.ConversationPersistenceService persistenceService =
                org.mockito.Mockito.mock(com.altstay.api.conversation.ConversationPersistenceService.class);
        chatService = new ChatService(chatClient, promptFactory, properties, java.util.Optional.of(persistenceService));

        org.springframework.security.core.context.SecurityContextHolder.clearContext();

        ChatRequest request = new ChatRequest("AltStay Property", "KB", List.of(), "check-in?", UUID.randomUUID(), null);
        chatService.answer(request);

        org.mockito.Mockito.verifyNoInteractions(persistenceService);
    }
}
