package com.altstay.api.chat.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record ChatRequest(
        String propertyName,
        @NotBlank @Size(max = 20_000) String knowledgeBase,
        @Size(max = 200) List<@Valid ChatTurn> history,
        @NotBlank @Size(max = 1_000) String message,
        UUID propertyId,
        UUID conversationId) {

    public ChatRequest {
        propertyName = (propertyName == null || propertyName.isBlank()) ? "AltStay Property" : propertyName.trim();
        history = history == null ? List.of() : List.copyOf(history);
    }

    public ChatRequest(String knowledgeBase, List<ChatTurn> history, String message) {
        this(null, knowledgeBase, history, message, null, null);
    }

    public ChatRequest(String propertyName, String knowledgeBase, List<ChatTurn> history, String message) {
        this(propertyName, knowledgeBase, history, message, null, null);
    }
}
