package com.ratelimiter.distributed.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Boots a real Redis instance for every test class that extends this — per the spec's "use
 * Testcontainers... not mocks, you want to test actual atomicity" requirement.
 *
 * <p>By default this spins up a disposable Redis 7 container via Testcontainers, which is
 * the intended mode for local dev machines and CI (e.g. GitHub Actions' Ubuntu runners ship
 * Docker out of the box). In a Docker-less sandbox, set {@code -DEXTERNAL_REDIS_HOST=host}
 * (and optionally {@code -DEXTERNAL_REDIS_PORT=port}) to point the same test suite at a
 * plain Redis process instead — the atomicity assertions being tested are identical either
 * way, since they exercise the real Lua script inside a real Redis, not a mock.
 */
public abstract class AbstractRedisIntegrationTest {

    private static final String EXTERNAL_HOST = System.getProperty("EXTERNAL_REDIS_HOST");
    private static final String EXTERNAL_PORT = System.getProperty("EXTERNAL_REDIS_PORT", "6379");

    private static GenericContainer<?> redisContainer;

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        if (EXTERNAL_HOST != null) {
            registry.add("spring.data.redis.host", () -> EXTERNAL_HOST);
            registry.add("spring.data.redis.port", () -> EXTERNAL_PORT);
            return;
        }

        redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
        redisContainer.start();
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
    }

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    @AfterEach
    void flushRedis() {
        redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }
}
