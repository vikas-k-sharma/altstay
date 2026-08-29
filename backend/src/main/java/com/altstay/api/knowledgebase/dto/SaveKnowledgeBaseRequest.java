package com.altstay.api.knowledgebase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveKnowledgeBaseRequest(
        @NotBlank(message = "content is required")
        @Size(max = 20_000, message = "content must not exceed 20000 characters")
        String content
) {}
