package com.altstay.api.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChatTurn(
        @NotNull Role role,
        @NotBlank @Size(max = 4_000) String content) {}
