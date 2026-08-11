package com.ratelimiter.distributed.model;

import lombok.Builder;
import lombok.Getter;

/**
 * Read-only view of a bucket's current state, used by the debug/status endpoint.
 * Unlike {@link RateLimitResult}, computing this never mutates Redis.
 */
@Getter
@Builder
public class BucketSnapshot {
    private final String clientKey;
    private final boolean exists;
    private final long tokens;
    private final long capacity;
    private final long refillRatePerSecond;
    private final long lastRefillEpochMs;
}
