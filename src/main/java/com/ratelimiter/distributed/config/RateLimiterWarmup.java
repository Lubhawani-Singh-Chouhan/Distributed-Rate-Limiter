package com.ratelimiter.distributed.config;

import com.ratelimiter.distributed.service.TokenBucketService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Opens the Lettuce pool and caches the Lua script SHA <em>before</em> Tomcat starts
 * accepting traffic. Without this, the first few thousand requests after boot pay for
 * TCP connect + AUTH + EVAL (not EVALSHA) + JIT, which shows up as a multi-second
 * latency spike even when the steady-state rate is fine.
 */
@Slf4j
@Component
public class RateLimiterWarmup implements InitializingBean {

    private final TokenBucketService tokenBucketService;
    private final int poolMinIdle;

    public RateLimiterWarmup(
            TokenBucketService tokenBucketService,
            @Value("${spring.data.redis.lettuce.pool.min-idle:64}") int poolMinIdle) {
        this.tokenBucketService = tokenBucketService;
        this.poolMinIdle = Math.max(1, poolMinIdle);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("Warming {} Redis connections and token_bucket.lua EVALSHA", poolMinIdle);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<Void>> tasks = new ArrayList<>(poolMinIdle);
            for (int i = 0; i < poolMinIdle; i++) {
                final int n = i;
                tasks.add(() -> {
                    tokenBucketService.tryConsume("__warmup__:" + n, 1, 1, 1);
                    return null;
                });
            }
            for (Future<Void> future : executor.invokeAll(tasks)) {
                future.get();
            }
        }
        log.info("Rate limiter hot path is ready");
    }
}
