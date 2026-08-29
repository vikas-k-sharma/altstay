package com.altstay.api.ratelimit;

import com.altstay.api.config.RateLimitProperties;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * In-process token-bucket limiter, per phase-4-completion.md §2.2 and §2.3.
 *
 * <p>Three keys, in this order: an authenticated request buckets on its {@code tenantId}; an
 * anonymous request buckets on the {@code x-altstay-session} header; and every anonymous request
 * additionally passes a global bucket, because a client-supplied key is trivially rotated.
 */
@Slf4j
public class RateLimiter {

    private static final String ANONYMOUS_DEFAULT_KEY = "anonymous-default";

    private final RateLimitProperties properties;
    private final LongSupplier clock;
    private final TokenBucket globalAnonymousBucket;
    private final ConcurrentHashMap<String, TokenBucket> sessionBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TokenBucket> tenantBuckets = new ConcurrentHashMap<>();

    public RateLimiter(RateLimitProperties properties) {
        this(properties, System::nanoTime);
    }

    public RateLimiter(RateLimitProperties properties, LongSupplier clock) {
        this.properties = properties;
        this.clock = clock;
        this.globalAnonymousBucket = new TokenBucket(
                properties.anonymousGlobalBurst(),
                properties.anonymousGlobalRefillDuration(),
                clock.getAsLong()
        );
    }

    public ConsumptionResult tryConsume(UUID tenantId, String sessionId) {
        long now = clock.getAsLong();

        if (tenantId != null) {
            TokenBucket bucket = bucketFor(tenantBuckets, tenantId.toString(), now,
                    properties.authenticatedTenantBurst(), properties.authenticatedTenantRefillDuration());
            return bucket.tryConsume(now, 1.0);
        }

        // The per-session bucket is checked FIRST. Consuming the global token before knowing whether
        // this session is allowed would let one hammering session drain the shared allowance for
        // every other guest while being rejected itself.
        String key = (sessionId != null && !sessionId.isBlank()) ? sessionId.trim() : ANONYMOUS_DEFAULT_KEY;
        TokenBucket sessionBucket = bucketFor(sessionBuckets, key, now,
                properties.anonymousSessionBurst(), properties.anonymousSessionRefillDuration());
        ConsumptionResult sessionResult = sessionBucket.tryConsume(now, 1.0);
        if (!sessionResult.allowed()) {
            return sessionResult;
        }

        // The global bucket bounds an impolite caller who rotates the session key on every request.
        return globalAnonymousBucket.tryConsume(now, 1.0);
    }

    private TokenBucket bucketFor(ConcurrentHashMap<String, TokenBucket> map, String key, long now,
                                  int burst, java.time.Duration refill) {
        TokenBucket existing = map.get(key);
        if (existing != null) {
            return existing;
        }
        evictIfOverCapacity(map, now);
        return map.computeIfAbsent(key, k -> new TokenBucket(burst, refill, now));
    }

    /**
     * Enforces a <em>hard</em> cap on the map.
     *
     * <p>An earlier revision only dropped entries older than {@code entryTtl}, which left a key
     * rotating faster than that TTL growing the map without bound - the exact thing the cap exists
     * to prevent. Idle entries are still preferred for eviction; if that is not enough to get back
     * under the cap, the least recently used entries go too, so the map size is bounded by
     * configuration rather than by the caller's politeness.
     */
    private void evictIfOverCapacity(ConcurrentHashMap<String, TokenBucket> map, long nowNanos) {
        int maxEntries = properties.maxEntries();
        if (map.size() < maxEntries) {
            return;
        }

        long ttlNanos = properties.entryTtl().toNanos();
        map.entrySet().removeIf(entry -> (nowNanos - entry.getValue().getLastAccessNanos()) > ttlNanos);

        int excess = map.size() - maxEntries + 1;
        if (excess <= 0) {
            return;
        }

        List<String> oldest = map.entrySet().stream()
                .sorted(Comparator.comparingLong(e -> e.getValue().getLastAccessNanos()))
                .limit(excess)
                .map(Map.Entry::getKey)
                .toList();
        oldest.forEach(map::remove);
        log.warn("Rate limiter bucket map hit its cap of {}; evicted {} least-recently-used entries",
                maxEntries, oldest.size());
    }

    int trackedSessionBucketCount() {
        return sessionBuckets.size();
    }
}
