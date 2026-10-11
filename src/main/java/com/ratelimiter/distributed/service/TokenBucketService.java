package com.ratelimiter.distributed.service;

import com.ratelimiter.distributed.config.RateLimiterProperties;
import com.ratelimiter.distributed.config.RateLimiterProperties.OnRedisFailure;
import com.ratelimiter.distributed.model.BucketSnapshot;
import com.ratelimiter.distributed.model.RateLimitResult;
import com.ratelimiter.distributed.util.RateLimitKeyBuilder;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

/**
 * Java-side entry point to the Redis-backed token bucket. All refill + consume
 * logic lives in {@code token_bucket.lua} and runs atomically inside Redis via
 * {@code EVALSHA}. Time is taken from Redis TIME inside the script — this class
 * does not pass a JVM clock.
 */
@Slf4j
@Service
public class TokenBucketService {

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisScript<List> tokenBucketScript;
    private final RateLimiterProperties properties;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong cooldownUntilMs = new AtomicLong();
    private final AtomicLong failOpenAllowCount = new AtomicLong();

    public TokenBucketService(
            RedisTemplate<String, String> redisTemplate,
            RedisScript<List> tokenBucketScript,
            RateLimiterProperties properties) {
        this.redisTemplate = redisTemplate;
        this.tokenBucketScript = tokenBucketScript;
        this.properties = properties;
    }

    @SuppressWarnings("unchecked")
    public RateLimitResult tryConsume(String clientKey, long capacity, long refillRatePerSecond, long requestedTokens) {
        if (inCooldown()) {
            return degrade("short-circuit cooldown", capacity);
        }

        String bucketKey = RateLimitKeyBuilder.bucketKey(clientKey);
        List<Long> result;
        try {
            result = redisTemplate.execute(
                    tokenBucketScript,
                    List.of(bucketKey),
                    String.valueOf(capacity),
                    String.valueOf(refillRatePerSecond),
                    String.valueOf(requestedTokens));
        } catch (RuntimeException e) {
            log.error("Redis token-bucket call failed for key {}: {}", bucketKey, e.getMessage());
            recordFailure();
            return degrade("execute failed", capacity);
        }

        if (result == null || result.size() < 3) {
            log.error("Unexpected empty response from token_bucket.lua for key {}", bucketKey);
            recordFailure();
            return degrade("empty lua response", capacity);
        }

        recordSuccess();
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
     * Read-only projection of a bucket's current state. Uses Redis TIME for the
     * refill projection so a skewed JVM clock cannot mis-report remaining tokens.
     */
    public BucketSnapshot peek(String clientKey, long capacity, long refillRatePerSecond) {
        String bucketKey = RateLimitKeyBuilder.bucketKey(clientKey);
        HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
        Map<String, String> raw = hashOps.entries(bucketKey);
        long nowMs = redisTimeMs();

        if (raw.isEmpty()) {
            return BucketSnapshot.builder()
                    .clientKey(clientKey)
                    .exists(false)
                    .tokens(capacity)
                    .capacity(capacity)
                    .refillRatePerSecond(refillRatePerSecond)
                    .lastRefillEpochMs(nowMs)
                    .build();
        }

        double storedTokens = Double.parseDouble(raw.getOrDefault("tokens", String.valueOf(capacity)));
        long lastRefill = Long.parseLong(raw.getOrDefault("last_refill", String.valueOf(nowMs)));
        long elapsedMs = Math.max(0, nowMs - lastRefill);
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

    public long getFailOpenAllowCount() {
        return failOpenAllowCount.get();
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    public boolean isShortCircuitOpen() {
        return inCooldown();
    }

    private boolean inCooldown() {
        return System.currentTimeMillis() < cooldownUntilMs.get();
    }

    private void recordFailure() {
        int n = consecutiveFailures.incrementAndGet();
        if (n >= properties.getRedisFailureThreshold()) {
            cooldownUntilMs.set(System.currentTimeMillis() + properties.getRedisFailureCooldownMs());
            log.warn("Redis short-circuit open for {}ms after {} consecutive failures",
                    properties.getRedisFailureCooldownMs(), n);
        }
    }

    private void recordSuccess() {
        consecutiveFailures.set(0);
        cooldownUntilMs.set(0);
    }

    private RateLimitResult degrade(String reason, long capacity) {
        if (properties.getOnRedisFailure() == OnRedisFailure.FAIL_OPEN) {
            long n = failOpenAllowCount.incrementAndGet();
            log.warn("Rate limiter fail-open ({}): allowing request. fail-open count={}", reason, n);
            return RateLimitResult.failOpen(capacity);
        }
        return RateLimitResult.failClosed(capacity);
    }

    private long redisTimeMs() {
        try {
            Long time = redisTemplate.execute((RedisCallback<Long>) connection ->
                    connection.serverCommands().time());
            if (time != null && time > 0) {
                return time;
            }
        } catch (RuntimeException e) {
            log.warn("Redis TIME failed, falling back to JVM clock: {}", e.getMessage());
        }
        return System.currentTimeMillis();
    }
}
