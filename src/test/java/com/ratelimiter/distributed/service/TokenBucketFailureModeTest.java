package com.ratelimiter.distributed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ratelimiter.distributed.config.RateLimiterProperties;
import com.ratelimiter.distributed.config.RateLimiterProperties.OnRedisFailure;
import com.ratelimiter.distributed.model.RateLimitResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class TokenBucketFailureModeTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private RedisScript<List> script;

    private RateLimiterProperties properties;
    private TokenBucketService service;

    @BeforeEach
    void setUp() {
        properties = new RateLimiterProperties();
        properties.setRedisFailureThreshold(2);
        properties.setRedisFailureCooldownMs(60_000);
        service = new TokenBucketService(redisTemplate, script, properties);
    }

    @Test
    void failClosedDeniesWhenRedisThrows() {
        properties.setOnRedisFailure(OnRedisFailure.FAIL_CLOSED);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any()))
                .thenThrow(new RedisConnectionFailureException("down"));

        RateLimitResult result = service.tryConsume("k", 50, 10);
        assertThat(result.isAllowed()).isFalse();
        assertThat(result.isRedisUnavailable()).isTrue();
        assertThat(service.getFailOpenAllowCount()).isZero();
    }

    @Test
    void failOpenAllowsAndCountsWhenRedisThrows() {
        properties.setOnRedisFailure(OnRedisFailure.FAIL_OPEN);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any()))
                .thenThrow(new RedisConnectionFailureException("down"));

        RateLimitResult result = service.tryConsume("k", 50, 10);
        assertThat(result.isAllowed()).isTrue();
        assertThat(result.isRedisUnavailable()).isTrue();
        assertThat(service.getFailOpenAllowCount()).isEqualTo(1);
    }

    @Test
    void shortCircuitSkipsRedisAfterThresholdFailures() {
        properties.setOnRedisFailure(OnRedisFailure.FAIL_CLOSED);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any()))
                .thenThrow(new RedisConnectionFailureException("down"));

        service.tryConsume("k", 50, 10);
        service.tryConsume("k", 50, 10);
        assertThat(service.isShortCircuitOpen()).isTrue();

        RateLimitResult third = service.tryConsume("k", 50, 10);
        assertThat(third.isRedisUnavailable()).isTrue();
        assertThat(third.isAllowed()).isFalse();
        verify(redisTemplate, times(2)).execute(any(RedisScript.class), anyList(), any(), any(), any());
    }
}
