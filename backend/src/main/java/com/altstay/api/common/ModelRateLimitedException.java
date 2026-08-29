package com.altstay.api.common;

/**
 * Thrown when the upstream AI model (e.g. Google Gemini) returns HTTP 429 Too Many Requests
 * or RESOURCE_EXHAUSTED (quota exhaustion).
 *
 * <p>Mapped to HTTP 503 Service Unavailable in {@link GlobalExceptionHandler} so callers
 * distinguish upstream quota pauses from application throttles (429) and outages (502).
 */
public class ModelRateLimitedException extends RuntimeException {

    public ModelRateLimitedException(String message) {
        super(message);
    }

    public ModelRateLimitedException(String message, Throwable cause) {
        super(message, cause);
    }
}
