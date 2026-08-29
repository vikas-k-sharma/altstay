package com.altstay.api.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Token-bucket budgets for {@code com.altstay.api.ratelimit}, per phase-4-completion.md §2.3.
 *
 * <p><strong>No defaults, and no repair.</strong> An earlier revision carried a compact constructor
 * that substituted a default for every invalid or null value. Because a compact constructor runs
 * <em>before</em> {@code @Validated}, that made every constraint below unreachable: a typo'd or
 * missing setting silently became a working default rather than a startup failure. That is the
 * inverse of the rule {@code ALTSTAY_DB_URL} and {@code GOOGLE_API_KEY} are held to, so the repair
 * is gone and the constraints are load-bearing again.
 *
 * <p>Consequently every value must be present in {@code application.yaml} — including
 * {@code max-entries} and {@code entry-ttl}, which the repair used to paper over.
 */
@Validated
@ConfigurationProperties("altstay.rate-limit")
public record RateLimitProperties(
        @Min(1) int anonymousSessionBurst,
        @NotNull Duration anonymousSessionRefillDuration,
        @Min(1) int anonymousGlobalBurst,
        @NotNull Duration anonymousGlobalRefillDuration,
        @Min(1) int authenticatedTenantBurst,
        @NotNull Duration authenticatedTenantRefillDuration,
        @Min(100) int maxEntries,
        @NotNull Duration entryTtl) {
}
