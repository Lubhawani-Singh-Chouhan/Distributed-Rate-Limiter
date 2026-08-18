package com.ratelimiter.distributed.service;

import com.ratelimiter.distributed.model.BucketSnapshot;
import com.ratelimiter.distributed.model.RateLimitResult;
import com.ratelimiter.distributed.util.RateLimitKeyBuilder;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

/**
 * Java-side entry point to the Redis-backed token bucket. All refill + consume
 * logic lives in {@code token_bucket.lua} and runs atomically inside Redis via
 * {@code EVALSHA} (Spring Data Redis transparently falls back to {@code EVAL} the
 * first time it sees a NOSCRIPT error, then the SHA is cached for every call after
 * that) — see the class Javadoc on {@link com.ratelimiter.distributed.config.RedisConfig}.
 *
 * <p>This class intentionally does no locking, no read-then-write from Java, and no
 * client-side arithmetic on token counts: every decision is made in one round trip
 * to Redis so that concurrent callers (whether threads in one JVM or requests spread
 * across many app instances) can never observe or act on a stale bucket state.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBucketService {

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisScript<List> tokenBucketScript;

    /**
     * Attempts to consume {@code requestedTokens} from the bucket identified by
     * {@code clientKey}, refilling it first based on elapsed time.
     *
     * @param clientKey       logical identity of the caller (API key, IP, user id, ...)
     * @param capacity        max tokens the bucket can hold (burst allowance)
     * @param refillRatePerSecond tokens added back per second
     * @param requestedTokens tokens this call wants to consume (usually 1)
     */
    @SuppressWarnings("unchecked")
    public RateLimitResult tryConsume(String clientKey, long capacity, long refillRatePerSecond, long requestedTokens) {
        String bucketKey = RateLimitKeyBuilder.bucketKey(clientKey);
        long nowMs = System.currentTimeMillis();

        List<Long> result;
        try {
            result = redisTemplate.execute(
                    tokenBucketScript,
                    List.of(bucketKey),
                    String.valueOf(capacity),
                    String.valueOf(refillRatePerSecond),
                    String.valueOf(nowMs),
                    String.valueOf(requestedTokens));
        } catch (RuntimeException e) {
            log.error("Redis token-bucket call failed for key {}: {}", bucketKey, e.getMessage());
            return RateLimitResult.denied(0, 1000, capacity);
        }

        if (result == null || result.size() < 3) {
            log.error("Unexpected empty response from token_bucket.lua for key {}", bucketKey);
            // Fail-open would silently disable protection; fail-closed is the safer default
            // for a rate limiter guarding shared infrastructure.
            return RateLimitResult.denied(0, 1000, capacity);
        }

        boolean allowed = result.get(0) == 1L;
        long remaining = result.get(1);
        long retryAfterMs = result.get(2);

        return allowed
                ? RateLimitResult.allowed(remaining, capacity)
                : RateLimitResult.denied(remaining, retryAfterMs, capacity);
    }

    public RateLimitResult tryConsume(String clientKey, long capacity, long refillRatePerSecond) {
        return tryConsume(clientKey, capacity, refillRatePerSecond, 1L);
    }

    /**
     * Read-only projection of a bucket's current state, for the debug/status endpoint.
     * Deliberately does <b>not</b> go through the Lua script: it must never mutate the
     * bucket, so there is no atomicity concern that would require it to.
     */
    public BucketSnapshot peek(String clientKey, long capacity, long refillRatePerSecond) {
        String bucketKey = RateLimitKeyBuilder.bucketKey(clientKey);
        HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
        Map<String, String> raw = hashOps.entries(bucketKey);

        if (raw.isEmpty()) {
            return BucketSnapshot.builder()
                    .clientKey(clientKey)
                    .exists(false)
                    .tokens(capacity)
                    .capacity(capacity)
                    .refillRatePerSecond(refillRatePerSecond)
                    .lastRefillEpochMs(System.currentTimeMillis())
                    .build();
        }

        double storedTokens = Double.parseDouble(raw.getOrDefault("tokens", String.valueOf(capacity)));
        long lastRefill = Long.parseLong(raw.getOrDefault("last_refill", String.valueOf(System.currentTimeMillis())));

        long elapsedMs = Math.max(0, System.currentTimeMillis() - lastRefill);
        double projectedTokens = Math.min(capacity, storedTokens + (elapsedMs / 1000.0) * refillRatePerSecond);

        return BucketSnapshot.builder()
                .clientKey(clientKey)
                .exists(true)
                .tokens((long) Math.floor(projectedTokens))
                .capacity(capacity)
                .refillRatePerSecond(refillRatePerSecond)
                .lastRefillEpochMs(lastRefill)
                .build();
    }
}
