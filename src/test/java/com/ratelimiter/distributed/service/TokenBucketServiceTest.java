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

    @Test
    void refillUsesRedisTimeSoACallerFutureTimestampCannotInflateTheBucket() {
        String clientKey = "clock-skew-client";
        long capacity = 5;
        long refillRate = 1;

        RateLimitResult drain = tokenBucketService.tryConsume(clientKey, capacity, refillRate, capacity);
        assertThat(drain.isAllowed()).isTrue();
        assertThat(tokenBucketService.tryConsume(clientKey, capacity, refillRate).isAllowed()).isFalse();

        // Old design: ARGV[3]=now, ARGV[4]=requested. A 24h-ahead instance would pass
        // now=now+86400000 and refill the empty bucket to capacity in one EVAL.
        // New design: ARGV[3]=requested; a fourth ARGV is ignored. Empty bucket stays denied.
        String bucketKey = RateLimitKeyBuilder.bucketKey(clientKey);
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptText(loadProductionScript());
        script.setResultType(List.class);

        @SuppressWarnings("unchecked")
        List<Long> withStrayFutureNow = redisTemplate.execute(
                script,
                List.of(bucketKey),
                "5",
                "1",
                "1",
                String.valueOf(System.currentTimeMillis() + 86_400_000L));

        assertThat(withStrayFutureNow.get(0)).as("must not treat a caller timestamp as now").isEqualTo(0L);
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

    private static String loadProductionScript() {
        return com.ratelimiter.distributed.util.LuaScriptLoader.load("scripts/token_bucket.lua");
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
