package com.altstay.api.ratelimit;

public record ConsumptionResult(boolean allowed, long retryAfterSeconds) {

    public static ConsumptionResult allow() {
        return new ConsumptionResult(true, 0);
    }

    public static ConsumptionResult reject(long retryAfterSeconds) {
        return new ConsumptionResult(false, retryAfterSeconds);
    }
}
