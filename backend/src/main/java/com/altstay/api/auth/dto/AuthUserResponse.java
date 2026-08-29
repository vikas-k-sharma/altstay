package com.altstay.api.auth.dto;

import java.util.Set;
import java.util.UUID;

public record AuthUserResponse(
        UUID userId,
        UUID tenantId,
        String tenantSlug,
        String email,
        String fullName,
        Set<String> roles
) {}
