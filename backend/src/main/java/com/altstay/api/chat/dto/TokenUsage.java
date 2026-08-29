package com.altstay.api.chat.dto;

public record TokenUsage(
        int promptTokens,
        int completionTokens,
        int totalTokens) {}
