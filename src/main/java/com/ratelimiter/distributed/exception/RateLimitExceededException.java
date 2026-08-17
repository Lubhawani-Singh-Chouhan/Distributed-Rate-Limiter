package com.ratelimiter.distributed.exception;

import com.ratelimiter.distributed.model.RateLimitResult;
import lombok.Getter;

/**
 * Thrown when a caller has exhausted their token bucket. Carries the
 * {@link RateLimitResult} so the exception handler can populate
 * {@code Retry-After} / {@code X-RateLimit-*} headers without recomputing anything.
 */
@Getter
public class RateLimitExceededException extends RuntimeException {

    private final String clientKey;
    private final RateLimitResult result;

    public RateLimitExceededException(String clientKey, RateLimitResult result) {
        super("Rate limit exceeded for client '" + clientKey + "'");
        this.clientKey = clientKey;
        this.result = result;
    }
}
