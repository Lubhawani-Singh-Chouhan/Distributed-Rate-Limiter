package com.ratelimiter.distributed.util;

/**
 * Builds the Redis key under which a client's token bucket hash lives.
 *
 * Namespacing under {@code ratelimit:bucket:} keeps the keyspace easy to inspect
 * (e.g. {@code redis-cli --scan --pattern 'ratelimit:bucket:*'}) and avoids collisions
 * with any other data Redis might be asked to hold in a shared deployment.
 */
public final class RateLimitKeyBuilder {

    private static final String PREFIX = "ratelimit:bucket:";

    private RateLimitKeyBuilder() {
    }

    public static String bucketKey(String clientKey) {
        return PREFIX + clientKey;
    }
}
