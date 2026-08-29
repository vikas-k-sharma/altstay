package com.altstay.api.chat.dto;

public record ChatResponse(
        String reply,
        boolean escalated,
        String model,
        TokenUsage usage,
        long latencyMs) {}
