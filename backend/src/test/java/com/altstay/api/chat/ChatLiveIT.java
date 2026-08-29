package com.altstay.api.chat;

import com.altstay.api.chat.dto.ChatRequest;
import com.altstay.api.chat.dto.ChatResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ALTSTAY_LIVE_TESTS", matches = "true")
class ChatLiveIT {

    @Autowired
    private ChatService chatService;

    @Autowired
    private ChatController chatController;

    @Test
    @DisplayName("Live Gemini call: Question answered from knowledge base is grounded and not escalated")
    void liveChat_inKnowledgeBaseQuestion_returnsGroundedAnswer() {
        String kb = "Check-in is strictly from 2:00 PM to 11:00 PM. Dorm beds cost 650 rupees per night. No pets allowed.";
        ChatRequest request = new ChatRequest(kb, List.of(), "what time can I check in?");

        ChatResponse response = chatController.chat(request);

        assertThat(response).isNotNull();
        assertThat(response.reply().toLowerCase()).contains("2");
        assertThat(response.escalated()).isFalse();
        assertThat(response.model()).isNotEmpty();
        assertThat(response.usage().totalTokens()).isGreaterThan(0);
        assertThat(response.latencyMs()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Live Gemini call: Question outside knowledge base triggers escalation")
    void liveChat_outOfKnowledgeBaseQuestion_triggersEscalation() {
        String kb = "Check-in is from 2:00 PM. Dorm beds cost 650 rupees per night.";
        ChatRequest request = new ChatRequest(kb, List.of(), "do you have a private helipad and airport helicopter shuttle?");

        ChatResponse response = chatController.chat(request);

        assertThat(response).isNotNull();
        assertThat(response.escalated()).isTrue();
        assertThat(response.reply()).isNotEmpty();
        assertThat(response.latencyMs()).isGreaterThan(0);
    }
}
