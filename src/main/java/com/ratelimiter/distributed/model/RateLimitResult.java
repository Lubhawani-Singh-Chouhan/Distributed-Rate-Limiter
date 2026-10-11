package com.ratelimiter.distributed.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Outcome of a single {@code tryConsume} call against the token bucket.
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
    /** True when Redis was skipped or failed; not a real bucket decision. */
    private final boolean redisUnavailable;

    public static RateLimitResult allowed(long remainingTokens, long capacity) {
        return new RateLimitResult(true, remainingTokens, 0L, capacity, false);
    }

    public static RateLimitResult denied(long remainingTokens, long retryAfterMs, long capacity) {
        return new RateLimitResult(false, remainingTokens, retryAfterMs, capacity, false);
    }

    public static RateLimitResult failClosed(long capacity) {
        return new RateLimitResult(false, 0L, 0L, capacity, true);
    }

    public static RateLimitResult failOpen(long capacity) {
        return new RateLimitResult(true, capacity, 0L, capacity, true);
    }
}
