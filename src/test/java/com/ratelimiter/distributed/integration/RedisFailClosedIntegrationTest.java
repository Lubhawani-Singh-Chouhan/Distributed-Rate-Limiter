package com.ratelimiter.distributed.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.ratelimiter.distributed.RateLimiterApplication;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.Assumptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(classes = RateLimiterApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "ratelimiter.on-redis-failure=fail-closed",
        "ratelimiter.redis-failure-threshold=2",
        "ratelimiter.redis-failure-cooldown-ms=60000",
        "spring.data.redis.timeout=200ms",
        "spring.data.redis.connect-timeout=200ms",
        "REDIS_COMMAND_TIMEOUT=200ms"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIf("com.ratelimiter.distributed.integration.RedisEnvironment#available")
class RedisFailClosedIntegrationTest {

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static {
        if (RedisEnvironment.available()) {
            REDIS.start();
        }
    }

    @BeforeAll
    static void requireRunningRedis() {
        Assumptions.assumeTrue(REDIS.isRunning(), "Docker Redis container must be running");
    }

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void failClosedReturns503WhenRedisIsStopped() {
        ResponseEntity<String> before = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/v1/resource", String.class);
        assertThat(before.getStatusCode()).isEqualTo(HttpStatus.OK);

        REDIS.stop();

        ResponseEntity<String> after = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/v1/resource", String.class);
        assertThat(after.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(after.getHeaders().getFirst("X-RateLimit-Degraded")).isEqualTo("true");
        assertThat(after.getBody()).contains("RATE_LIMITER_UNAVAILABLE");
    }
}
