package com.ratelimiter.distributed.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Resolved token-bucket parameters for a given request: how big the bucket is
 * and how fast it refills. Produced by merging global defaults with any
 * per-endpoint ({@code @RateLimit}) or per-client overrides.
 */
@Getter
@Builder
@AllArgsConstructor
public class RateLimitRule {

    private final long capacity;
    private final long refillRatePerSecond;
    private final long requestedTokens;

    /**
     * Distinguishes which "bucket" a request belongs to for a given client, so that a
     * per-endpoint override (different capacity/refillRate) never shares Redis state with
     * the global default bucket, or with a different override. See
     * {@link com.ratelimiter.distributed.util.RateLimitKeyBuilder}.
     */
    private final String scope;

    public static RateLimitRule of(long capacity, long refillRatePerSecond, long requestedTokens, String scope) {
        return RateLimitRule.builder()
                .capacity(capacity)
                .refillRatePerSecond(refillRatePerSecond)
                .requestedTokens(requestedTokens)
                .scope(scope)
                .build();
    }
}
