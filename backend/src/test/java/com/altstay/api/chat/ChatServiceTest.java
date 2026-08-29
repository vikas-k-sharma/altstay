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

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
        properties = new ConciergeProperties(3, 1000, 20000, "property manager", "AltStay Hostel");
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
}
