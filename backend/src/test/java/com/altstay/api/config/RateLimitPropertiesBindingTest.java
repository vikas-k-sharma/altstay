package com.altstay.api.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Binds {@code altstay.rate-limit} out of the <strong>main</strong> {@code application.yaml}.
 *
 * <p>Two things are proven here that nothing else in the repo proves:
 *
 * <ol>
 *   <li>The production budgets are the ones phase-4-completion.md §2.3 specifies. The test
 *       classpath's {@code application.yaml} replaces the main file rather than merging with it, so
 *       the two can drift apart silently - the limiter's own tests would keep passing against test
 *       values while production ran on something else entirely.</li>
 *   <li>{@link RateLimitProperties} really fails on a bad value. An earlier revision carried a
 *       compact constructor that repaired every invalid input before {@code @Validated} could see
 *       it, which made all of its constraints unreachable.</li>
 * </ol>
 */
class RateLimitPropertiesBindingTest {

    @Test
    @DisplayName("Main application.yaml binds the §2.3 budgets: session 10/6s, global 60/1s")
    void mainApplicationYaml_bindsSpecifiedBudgets() throws IOException {
        RateLimitProperties properties = bindFromMainApplicationYaml();

        assertThat(properties.anonymousSessionBurst()).isEqualTo(10);
        assertThat(properties.anonymousSessionRefillDuration()).isEqualTo(Duration.ofSeconds(6));
        assertThat(properties.anonymousGlobalBurst()).isEqualTo(60);
        assertThat(properties.anonymousGlobalRefillDuration()).isEqualTo(Duration.ofSeconds(1));
        assertThat(properties.authenticatedTenantBurst()).isEqualTo(60);
        assertThat(properties.authenticatedTenantRefillDuration()).isEqualTo(Duration.ofSeconds(1));
        assertThat(properties.maxEntries()).isGreaterThanOrEqualTo(100);
        assertThat(properties.entryTtl()).isNotNull();
    }

    @Test
    @DisplayName("Main application.yaml supplies every key, so binding needs no default to fall back on")
    void mainApplicationYaml_leavesNoKeyToBeDefaulted() throws IOException {
        StandardEnvironment environment = mainApplicationYamlEnvironment();

        assertThat(environment.getProperty("altstay.rate-limit.max-entries")).isNotBlank();
        assertThat(environment.getProperty("altstay.rate-limit.entry-ttl")).isNotBlank();
    }

    @Test
    @DisplayName("A zero burst is rejected by validation rather than silently repaired to a default")
    void invalidBurst_failsValidationInsteadOfBeingDefaulted() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();

        RateLimitProperties invalid = new RateLimitProperties(
                0, Duration.ofSeconds(6),
                60, Duration.ofSeconds(1),
                60, Duration.ofSeconds(1),
                10_000, Duration.ofHours(1));

        Set<ConstraintViolation<RateLimitProperties>> violations = validator.validate(invalid);

        assertThat(violations)
                .as("@Min(1) on anonymousSessionBurst must be reachable - a compact constructor that "
                        + "repairs the value first would make this set empty")
                .isNotEmpty();
        assertThat(invalid.anonymousSessionBurst())
                .as("the invalid value must survive intact, not be rewritten to 10")
                .isZero();

        factory.close();
    }

    @Test
    @DisplayName("Binding a missing entry-ttl fails fast instead of defaulting")
    void missingEntryTtl_failsBinding() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                "partial-rate-limit",
                java.util.Map.of(
                        "altstay.rate-limit.anonymous-session-burst", 10,
                        "altstay.rate-limit.anonymous-session-refill-duration", "6s",
                        "altstay.rate-limit.anonymous-global-burst", 60,
                        "altstay.rate-limit.anonymous-global-refill-duration", "1s",
                        "altstay.rate-limit.authenticated-tenant-burst", 60,
                        "altstay.rate-limit.authenticated-tenant-refill-duration", "1s",
                        "altstay.rate-limit.max-entries", 10_000)));

        RateLimitProperties bound = new Binder(ConfigurationPropertySources.get(environment))
                .bind("altstay.rate-limit", RateLimitProperties.class)
                .orElseThrow(() -> new AssertionError("binding produced nothing at all"));

        assertThat(bound.entryTtl())
                .as("a missing entry-ttl must arrive as null, not be quietly repaired to one hour")
                .isNull();

        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Set<ConstraintViolation<RateLimitProperties>> violations = factory.getValidator().validate(bound);
        assertThat(violations)
                .as("@NotNull on entryTtl must reject the incomplete configuration at startup")
                .isNotEmpty();
        factory.close();
    }

    private RateLimitProperties bindFromMainApplicationYaml() throws IOException {
        StandardEnvironment environment = mainApplicationYamlEnvironment();
        return new Binder(ConfigurationPropertySources.get(environment))
                .bind("altstay.rate-limit", RateLimitProperties.class)
                .orElseThrow(() -> new AssertionError("altstay.rate-limit is absent from the main application.yaml"));
    }

    private StandardEnvironment mainApplicationYamlEnvironment() throws IOException {
        return MainApplicationYamlTestSupport.environmentWith(java.util.Map.of());
    }
}
