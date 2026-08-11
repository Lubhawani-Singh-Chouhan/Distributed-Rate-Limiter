package com.ratelimiter.distributed.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ratelimiter.distributed.RateLimiterApplication;
import com.ratelimiter.distributed.integration.AbstractRedisIntegrationTest;
import com.ratelimiter.distributed.model.RateLimitResult;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Exercises {@link TokenBucketService} against a real Redis instance (see
 * {@link AbstractRedisIntegrationTest}), covering the exact scenarios called out in
 * spec section 1.5: fresh-bucket burst, depletion, time-based refill, and — the one that
 * actually proves the Lua script is atomic — many threads hammering the same key
 * concurrently never over-allowing beyond capacity.
 */
@SpringBootTest(classes = RateLimiterApplication.class)
class TokenBucketServiceTest extends AbstractRedisIntegrationTest {

    @Autowired
    private TokenBucketService tokenBucketService;

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
