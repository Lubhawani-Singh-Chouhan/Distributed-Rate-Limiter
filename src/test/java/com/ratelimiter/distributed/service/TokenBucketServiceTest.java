package com.ratelimiter.distributed.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ratelimiter.distributed.RateLimiterApplication;
import com.ratelimiter.distributed.integration.AbstractRedisIntegrationTest;
import com.ratelimiter.distributed.model.RateLimitResult;
import com.ratelimiter.distributed.util.RateLimitKeyBuilder;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * Exercises {@link TokenBucketService} against a real Redis instance (see
 * {@link AbstractRedisIntegrationTest}), covering the exact scenarios called out in
 * spec section 1.5: fresh-bucket burst, depletion, time-based refill, and — the one that
 * actually proves the Lua script is atomic — many threads hammering the same key
 * concurrently never over-allowing beyond capacity.
 */
@SpringBootTest(classes = RateLimiterApplication.class)
@EnabledIf("com.ratelimiter.distributed.integration.RedisEnvironment#available")
class TokenBucketServiceTest extends AbstractRedisIntegrationTest {

    @Autowired
    private TokenBucketService tokenBucketService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Test
    void freshBucketAllowsBurstUpToCapacity() {
        String clientKey = "burst-client";
        long capacity = 5;
        long refillRate = 1;

        for (int i = 0; i < capacity; i++) {
            RateLimitResult result = tokenBucketService.tryConsume(clientKey, capacity, refillRate);
            assertThat(result.isAllowed()).as("request #%d should be allowed", i + 1).isTrue();
        }

        RateLimitResult overflow = tokenBucketService.tryConsume(clientKey, capacity, refillRate);
        assertThat(overflow.isAllowed()).isFalse();
    }

    @Test
    void bucketDepletesCorrectlyAndReportsRemainingTokens() {
        String clientKey = "depleting-client";
        long capacity = 3;
        long refillRate = 1;

        RateLimitResult first = tokenBucketService.tryConsume(clientKey, capacity, refillRate);
        assertThat(first.isAllowed()).isTrue();
        assertThat(first.getRemainingTokens()).isEqualTo(2);

        RateLimitResult second = tokenBucketService.tryConsume(clientKey, capacity, refillRate);
        assertThat(second.isAllowed()).isTrue();
        assertThat(second.getRemainingTokens()).isEqualTo(1);

        RateLimitResult third = tokenBucketService.tryConsume(clientKey, capacity, refillRate);
        assertThat(third.isAllowed()).isTrue();
        assertThat(third.getRemainingTokens()).isEqualTo(0);

        RateLimitResult fourth = tokenBucketService.tryConsume(clientKey, capacity, refillRate);
        assertThat(fourth.isAllowed()).isFalse();
        assertThat(fourth.getRemainingTokens()).isEqualTo(0);
        assertThat(fourth.getRetryAfterMs()).isGreaterThan(0);
    }

    @Test
    void refillHappensCorrectlyAfterTimePasses() throws InterruptedException {
        String clientKey = "refill-client";
        long capacity = 5;
        long refillRate = 5; // 5 tokens/sec -> ~1 token every 200ms

        // Drain the bucket completely.
        RateLimitResult drain = tokenBucketService.tryConsume(clientKey, capacity, refillRate, capacity);
        assertThat(drain.isAllowed()).isTrue();
        assertThat(drain.getRemainingTokens()).isEqualTo(0);

        RateLimitResult immediateRetry = tokenBucketService.tryConsume(clientKey, capacity, refillRate);
        assertThat(immediateRetry.isAllowed()).isFalse();

        // Sleep long enough to refill at least 2 tokens (400ms @ 5/sec = 2 tokens).
        Thread.sleep(500);

        RateLimitResult afterRefill = tokenBucketService.tryConsume(clientKey, capacity, refillRate);
        assertThat(afterRefill.isAllowed()).isTrue();
    }

    @Test
    void neverAllowsMoreThanCapacityUnderConcurrentLoadOnTheSameKey() throws InterruptedException {
        String clientKey = "concurrent-client";
        long capacity = 50;
        long refillRate = 0; // no refill during the test window, isolates pure consumption correctness
        int threadCount = 32;
        int attemptsPerThread = 10;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger allowedCount = new AtomicInteger();

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < attemptsPerThread; i++) {
                        RateLimitResult result = tokenBucketService.tryConsume(clientKey, capacity, refillRate);
                        if (result.isAllowed()) {
                            allowedCount.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).as("all threads finished in time").isTrue();
        // The critical assertion: no matter how many threads hammer the same key concurrently,
        // the total number of allowed requests must never exceed the bucket's capacity. If the
        // Lua script were not atomic (e.g. a naive Java GET-then-SET), this would flake upward
        // of `capacity` under load.
        assertThat(allowedCount.get()).isLessThanOrEqualTo((int) capacity);
        assertThat(allowedCount.get()).isEqualTo((int) capacity);
    }

    @Test
    void supportsMultiTokenRequestsInASingleCall() {
        String clientKey = "multi-token-client";
        long capacity = 10;
        long refillRate = 1;

        RateLimitResult result = tokenBucketService.tryConsume(clientKey, capacity, refillRate, 7);
        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getRemainingTokens()).isEqualTo(3);

        RateLimitResult overflow = tokenBucketService.tryConsume(clientKey, capacity, refillRate, 5);
        assertThat(overflow.isAllowed()).isFalse();
    }

    /**
     * One hour ahead of Redis TIME. The pre-fix script took this value as {@code now}
     * (ARGV[3]) and would refill an empty bucket to full. Production Java never sends it.
     */
    private static final long TEST_CLOCK_OFFSET_MS = 3_600_000L;

    /**
     * Pre-fix contract, kept only in this test: caller supplies {@code now}.
     * ARGV = capacity, refill_rate, now_ms, requested.
     */
    private static final String LEGACY_CALLER_CLOCK_SCRIPT = """
            local capacity = tonumber(ARGV[1])
            local refill_rate = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local requested = tonumber(ARGV[4])
            local bucket = redis.call('HMGET', KEYS[1], 'tokens', 'last_refill')
            local tokens = tonumber(bucket[1])
            local last_refill = tonumber(bucket[2])
            if tokens == nil or last_refill == nil then
              tokens = capacity
              last_refill = now
            end
            local elapsed_ms = now - last_refill
            if elapsed_ms < 0 then elapsed_ms = 0 end
            local new_tokens = math.min(capacity, tokens + (elapsed_ms / 1000.0) * refill_rate)
            local allowed = 0
            if new_tokens >= requested then
              new_tokens = new_tokens - requested
              allowed = 1
            end
            redis.call('HSET', KEYS[1], 'tokens', tostring(new_tokens), 'last_refill', tostring(now))
            return {allowed, math.floor(new_tokens), 0}
            """;

    @Test
    void callerClockOffsetRefillsLegacyScriptButNotRedisTimeScript() {
        long capacity = 5;
        long refillRate = 1;

        String legacyKey = "legacy-clock-skew";
        assertThat(tokenBucketService.tryConsume(legacyKey, capacity, refillRate, capacity).isAllowed()).isTrue();
        assertThat(tokenBucketService.tryConsume(legacyKey, capacity, refillRate).isAllowed())
                .as("bucket is empty before the skewed call")
                .isFalse();

        long skewedNow = redisTimeMs() + TEST_CLOCK_OFFSET_MS;
        List<Long> legacy = eval(LEGACY_CALLER_CLOCK_SCRIPT, legacyKey,
                String.valueOf(capacity),
                String.valueOf(refillRate),
                String.valueOf(skewedNow),
                "1");
        assertThat(legacy.get(0))
                .as("old design: a +1h caller clock refills an empty bucket and allows")
                .isEqualTo(1L);

        String currentKey = "redis-time-clock-skew";
        assertThat(tokenBucketService.tryConsume(currentKey, capacity, refillRate, capacity).isAllowed()).isTrue();
        assertThat(tokenBucketService.tryConsume(currentKey, capacity, refillRate).isAllowed()).isFalse();

        // Same offset exists in the test. Production tryConsume has no now parameter,
        // and token_bucket.lua reads Redis TIME, so the bucket stays empty.
        RateLimitResult unaffected = tokenBucketService.tryConsume(currentKey, capacity, refillRate);
        assertThat(unaffected.isAllowed())
                .as("new design ignores a +1h instance clock and stays denied")
                .isFalse();
        assertThat(unaffected.getRemainingTokens()).isZero();
    }

    @Test
    void refillAnchoredToRedisTimeAllowsTokensAfterElapsedRedisTime() {
        String clientKey = "redis-time-refill-client";
        long capacity = 5;
        long refillRate = 5;

        assertThat(tokenBucketService.tryConsume(clientKey, capacity, refillRate, capacity).isAllowed()).isTrue();
        assertThat(tokenBucketService.tryConsume(clientKey, capacity, refillRate).isAllowed()).isFalse();

        String bucketKey = RateLimitKeyBuilder.bucketKey(clientKey);
        DefaultRedisScript<Long> rewind = new DefaultRedisScript<>(
                "local t = redis.call('TIME') "
                        + "local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000) "
                        + "redis.call('HSET', KEYS[1], 'tokens', '0', 'last_refill', tostring(now - 2000)) "
                        + "return now",
                Long.class);
        redisTemplate.execute(rewind, List.of(bucketKey));

        RateLimitResult afterRedisElapsed = tokenBucketService.tryConsume(clientKey, capacity, refillRate);
        assertThat(afterRedisElapsed.isAllowed())
                .as("2s of Redis TIME elapsed at 5 tokens/s must refill the empty bucket")
                .isTrue();
    }

    @Test
    void idleBucketKeyExpires() throws InterruptedException {
        String clientKey = "ttl-client";
        long capacity = 1;
        long refillRate = 1; // time-to-full = 1s; PEXPIRE = 2s (2x margin)

        assertThat(tokenBucketService.tryConsume(clientKey, capacity, refillRate).isAllowed()).isTrue();
        String bucketKey = RateLimitKeyBuilder.bucketKey(clientKey);
        Long ttlMs = redisTemplate.getExpire(bucketKey, TimeUnit.MILLISECONDS);
        assertThat(ttlMs).isNotNull();
        assertThat(ttlMs).isGreaterThan(0L);
        assertThat(ttlMs).isLessThanOrEqualTo(2_500L);

        Thread.sleep(2_600);
        assertThat(redisTemplate.hasKey(bucketKey)).isFalse();
    }

    private long redisTimeMs() {
        DefaultRedisScript<Long> time = new DefaultRedisScript<>(
                "local t = redis.call('TIME') "
                        + "return tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)",
                Long.class);
        Long now = redisTemplate.execute(time, List.of("ratelimit:clock"));
        assertThat(now).isNotNull();
        return now;
    }

    @SuppressWarnings("unchecked")
    private List<Long> eval(String scriptText, String clientKey, String... args) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptText(scriptText);
        script.setResultType(List.class);
        return redisTemplate.execute(script, List.of(RateLimitKeyBuilder.bucketKey(clientKey)), (Object[]) args);
    }

    @Test
    void independentClientKeysDoNotShareBuckets() {
        long capacity = 2;
        long refillRate = 0;

        List<RateLimitResult> resultsA = List.of(
                tokenBucketService.tryConsume("tenant-a", capacity, refillRate),
                tokenBucketService.tryConsume("tenant-a", capacity, refillRate));
        List<RateLimitResult> resultsB = List.of(
                tokenBucketService.tryConsume("tenant-b", capacity, refillRate),
                tokenBucketService.tryConsume("tenant-b", capacity, refillRate));

        assertThat(resultsA).allMatch(RateLimitResult::isAllowed);
        assertThat(resultsB).allMatch(RateLimitResult::isAllowed);
    }
}
