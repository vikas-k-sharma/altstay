package com.altstay.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "tenantSlug is required") String tenantSlug,
        @NotBlank(message = "email is required") String email,
        @NotBlank(message = "password is required") String password
) {}
