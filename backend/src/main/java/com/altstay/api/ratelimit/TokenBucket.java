package com.altstay.api.ratelimit;

import java.time.Duration;

public class TokenBucket {

    private final double capacity;
    private final double tokensPerNano;
    private double availableTokens;
    private long lastRefillNanos;
    private volatile long lastAccessNanos;

    public TokenBucket(int capacity, Duration refillDuration, long initialNanos) {
        this.capacity = capacity;
        this.availableTokens = capacity;
        this.tokensPerNano = 1.0 / (double) refillDuration.toNanos();
        this.lastRefillNanos = initialNanos;
        this.lastAccessNanos = initialNanos;
    }

    public synchronized ConsumptionResult tryConsume(long nowNanos, double tokensToConsume) {
        long elapsedNanos = Math.max(0, nowNanos - lastRefillNanos);
        availableTokens = Math.min(capacity, availableTokens + (elapsedNanos * tokensPerNano));
        lastRefillNanos = nowNanos;
        lastAccessNanos = nowNanos;

        if (availableTokens >= tokensToConsume) {
            availableTokens -= tokensToConsume;
            return ConsumptionResult.allow();
        }

        double deficit = tokensToConsume - availableTokens;
        double nanosNeeded = deficit / tokensPerNano;
        long retryAfterSeconds = Math.max(1, (long) Math.ceil(nanosNeeded / 1_000_000_000.0));
        return ConsumptionResult.reject(retryAfterSeconds);
    }

    public long getLastAccessNanos() {
        return lastAccessNanos;
    }

    public double getAvailableTokens() {
        return availableTokens;
    }
}
