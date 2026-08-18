package com.ratelimiter.distributed.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Outcome of a single {@code tryConsume} call against the token bucket.
 *
 * @param allowed        whether the request is permitted to proceed
 * @param remainingTokens tokens left in the bucket immediately after this decision
 * @param retryAfterMs   suggested wait time (ms) before retrying, only meaningful when denied
 * @param capacity       the bucket's configured capacity, echoed back for header reporting
 */
@Getter
@Builder
@AllArgsConstructor
@ToString
public class RateLimitResult {

    private final boolean allowed;
    private final long remainingTokens;
    private final long retryAfterMs;
    private final long capacity;

    public static RateLimitResult allowed(long remainingTokens, long capacity) {
        return new RateLimitResult(true, remainingTokens, 0L, capacity);
    }

    public static RateLimitResult denied(long remainingTokens, long retryAfterMs, long capacity) {
        return new RateLimitResult(false, remainingTokens, retryAfterMs, capacity);
    }
}
