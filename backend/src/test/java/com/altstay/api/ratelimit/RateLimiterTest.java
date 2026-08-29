package com.altstay.api.ratelimit;

import com.altstay.api.config.RateLimitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    private AtomicLong clockNanos;
    private RateLimitProperties properties;
    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        clockNanos = new AtomicLong(1_000_000_000L);
        properties = new RateLimitProperties(
                10,
                Duration.ofSeconds(6),
                60,
                Duration.ofSeconds(1),
                60,
                Duration.ofSeconds(1),
                1_000,
                Duration.ofHours(1)
        );
        rateLimiter = new RateLimiter(properties, clockNanos::get);
    }

    @Test
    @DisplayName("A key rotating faster than the TTL cannot grow the bucket map past its cap")
    void rotatingSessionIds_cannotGrowTheMapWithoutBound() {
        // 200 buckets, cap 100. Every request lands within the 1-hour TTL, so nothing is evictable
        // on age alone - an earlier revision only dropped entries older than the TTL and therefore
        // grew without bound exactly here, which is the thing the cap exists to prevent.
        RateLimitProperties tightCap = new RateLimitProperties(
                10, Duration.ofSeconds(6),
                100_000, Duration.ofNanos(1),
                60, Duration.ofSeconds(1),
                100, Duration.ofHours(1));
        RateLimiter limiter = new RateLimiter(tightCap, clockNanos::get);

        for (int i = 0; i < 200; i++) {
            clockNanos.addAndGet(Duration.ofMillis(1).toNanos());
            limiter.tryConsume(null, "rotating-" + i);
        }

        assertThat(limiter.trackedSessionBucketCount())
                .as("the bucket map must be bounded by configuration, not by the caller's politeness")
                .isLessThanOrEqualTo(100);
    }

    @Test
    @DisplayName("A throttled session does not drain the global bucket for everyone else")
    void rejectedSession_doesNotConsumeGlobalTokens() {
        String greedy = "greedy-session";
        for (int i = 0; i < 10; i++) {
            assertThat(rateLimiter.tryConsume(null, greedy).allowed()).isTrue();
        }

        // 50 further attempts, all rejected by the greedy session's own bucket. If the global token
        // were taken before the session bucket was consulted, these would consume the shared
        // allowance and punish every other guest for one caller's behaviour.
        for (int i = 0; i < 50; i++) {
            assertThat(rateLimiter.tryConsume(null, greedy).allowed()).isFalse();
        }

        // Global capacity is 60 and only 10 tokens have legitimately been spent.
        for (int i = 0; i < 50; i++) {
            assertThat(rateLimiter.tryConsume(null, "polite-guest-" + i).allowed())
                    .as("polite guest %d must still be served", i + 1)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Burst of 10 requests is allowed immediately without throttling")
    void burst_allowsConfiguredCapacityImmediately() {
        String session = "test-session-1";
        for (int i = 0; i < 10; i++) {
            ConsumptionResult res = rateLimiter.tryConsume(null, session);
            assertThat(res.allowed()).as("Request %d should be allowed", i + 1).isTrue();
        }

        // 11th request fails
        ConsumptionResult eleventh = rateLimiter.tryConsume(null, session);
        assertThat(eleventh.allowed()).isFalse();
        assertThat(eleventh.retryAfterSeconds()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Refill: advancing clock by 6 seconds restores 1 token after exhaustion")
    void refill_restoresTokensAccordingToRefillDuration() {
        String session = "test-session-refill";
        for (int i = 0; i < 10; i++) {
            assertThat(rateLimiter.tryConsume(null, session).allowed()).isTrue();
        }

        // Exhausted
        assertThat(rateLimiter.tryConsume(null, session).allowed()).isFalse();

        // Advance clock by 6 seconds (6_000_000_000 nanos)
        clockNanos.addAndGet(Duration.ofSeconds(6).toNanos());

        ConsumptionResult refilledRes = rateLimiter.tryConsume(null, session);
        assertThat(refilledRes.allowed()).as("After 6 seconds, 1 token should be available").isTrue();

        // Immediate next is rejected
        assertThat(rateLimiter.tryConsume(null, session).allowed()).isFalse();
    }

    @Test
    @DisplayName("Act 2 demo flow: burst of 4 suggested question chips + 1 Retry in 2 seconds is NOT throttled")
    void demoAct2_chipBurstPlusRetry_isNotThrottled() {
        String session = "demo-user-session";
        // 4 clicks rapidly within 1.5 seconds
        for (int i = 0; i < 4; i++) {
            clockNanos.addAndGet(Duration.ofMillis(300).toNanos());
            assertThat(rateLimiter.tryConsume(null, session).allowed())
                    .as("Suggested question chip %d should succeed", i + 1)
                    .isTrue();
        }

        // 5th request: user clicks Retry after 500ms (total 2s)
        clockNanos.addAndGet(Duration.ofMillis(500).toNanos());
        ConsumptionResult retryResult = rateLimiter.tryConsume(null, session);
        assertThat(retryResult.allowed()).as("Retry request within 2s should be allowed").isTrue();
    }

    @Test
    @DisplayName("Two session IDs do not share a bucket")
    void distinctSessions_haveIndependentBuckets() {
        String sessionA = "user-a";
        String sessionB = "user-b";

        // Exhaust session A
        for (int i = 0; i < 10; i++) {
            assertThat(rateLimiter.tryConsume(null, sessionA).allowed()).isTrue();
        }
        assertThat(rateLimiter.tryConsume(null, sessionA).allowed()).isFalse();

        // Session B is fresh and unaffected
        for (int i = 0; i < 10; i++) {
            assertThat(rateLimiter.tryConsume(null, sessionB).allowed())
                    .as("Session B request %d should be allowed", i + 1)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Rotating session IDs are still bounded by the global anonymous bucket")
    void rotatingSessionIds_areBoundedByGlobalBucket() {
        // Send 60 requests with 60 unique session IDs
        for (int i = 0; i < 60; i++) {
            String randomSession = "rotating-session-" + i;
            assertThat(rateLimiter.tryConsume(null, randomSession).allowed())
                    .as("Global request %d should be allowed", i + 1)
                    .isTrue();
        }

        // 61st request from a new 61st session ID is rejected because global bucket is exhausted
        String sixtyFirstSession = "rotating-session-61";
        ConsumptionResult res = rateLimiter.tryConsume(null, sixtyFirstSession);
        assertThat(res.allowed()).as("61st request should be rejected by global bucket").isFalse();
        assertThat(res.retryAfterSeconds()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Authenticated requests key on tenantId and do not consume anonymous buckets")
    void authenticatedRequests_keyOnTenantId() {
        UUID tenantId = UUID.randomUUID();

        // Exhaust global anonymous bucket
        for (int i = 0; i < 60; i++) {
            assertThat(rateLimiter.tryConsume(null, "anon-" + i).allowed()).isTrue();
        }
        assertThat(rateLimiter.tryConsume(null, "anon-next").allowed()).isFalse();

        // Authenticated request for tenant is still allowed!
        ConsumptionResult tenantRes = rateLimiter.tryConsume(tenantId, null);
        assertThat(tenantRes.allowed()).as("Authenticated tenant request should not be blocked by anonymous global bucket").isTrue();
    }
}
