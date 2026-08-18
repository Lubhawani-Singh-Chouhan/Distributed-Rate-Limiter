package com.ratelimiter.distributed.config;

import com.ratelimiter.distributed.util.LuaScriptLoader;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.TimeoutOptions;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Wires up the Redis connection, a String-keyed/valued {@link RedisTemplate},
 * and the {@code token_bucket.lua} script bean.
 *
 * Spring Data Redis's {@link org.springframework.data.redis.core.script.DefaultScriptExecutor}
 * already does the EVALSHA-first-then-EVAL-on-NOSCRIPT dance for us, so declaring the script
 * once here (as a singleton {@link RedisScript}) is all that's needed to get the "cache the
 * SHA, avoid resending the body" behavior described in the spec.
 */
@Configuration
@EnableConfigurationProperties(RateLimiterProperties.class)
public class RedisConfig {

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public LettuceClientConfigurationBuilderCustomizer lettuceSteadyStateCustomizer() {
        return builder -> builder
                .commandTimeout(Duration.ofSeconds(1))
                .clientOptions(ClientOptions.builder()
                        .autoReconnect(true)
                        .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                        .timeoutOptions(TimeoutOptions.enabled(Duration.ofSeconds(1)))
                        .build());
    }

    @Bean
    public RedisScript<List> tokenBucketScript() {
        String scriptBody = LuaScriptLoader.load("scripts/token_bucket.lua");
        return new DefaultRedisScript<>(scriptBody, List.class);
    }
}
