package com.altstay.api.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties("altstay.concierge")
@Validated
public record ConciergeProperties(
        @Min(2) int maxHistoryTurns,
        @Min(1) int maxMessageChars,
        @Min(1) int maxKnowledgeBaseChars,
        @NotBlank String escalationContact,
        @NotBlank String propertyName,
        @NotNull Duration modelConnectTimeout,
        @NotNull Duration modelReadTimeout) {

    // No defaulting for the timeouts: a compact-constructor default makes @NotNull unreachable and
    // turns a missing value into a silent one. Phase 1 review #12 flagged exactly this shape on
    // propertyName. application.yaml is the single source; absence must fail binding, loudly.
    public ConciergeProperties {
        if (propertyName == null || propertyName.isBlank()) {
            propertyName = "AltStay Property";
        }
    }
}
