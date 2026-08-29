package com.altstay.api.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("altstay.concierge")
@Validated
public record ConciergeProperties(
        @Min(2) int maxHistoryTurns,
        @Min(1) int maxMessageChars,
        @Min(1) int maxKnowledgeBaseChars,
        @NotBlank String escalationContact,
        @NotBlank String propertyName) {

    public ConciergeProperties {
        if (propertyName == null || propertyName.isBlank()) {
            propertyName = "AltStay Property";
        }
    }
}
