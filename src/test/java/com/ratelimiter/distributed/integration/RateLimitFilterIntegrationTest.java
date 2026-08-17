package com.ratelimiter.distributed.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.ratelimiter.distributed.RateLimiterApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

/**
 * End-to-end proof of Phase 2's deliverable: hitting a demo endpoint past its configured
 * limit returns a proper 429 with quota headers, while unthrottled endpoints are unaffected.
 */
@SpringBootTest(classes = RateLimiterApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "ratelimiter.default-capacity=5",
        "ratelimiter.default-refill-rate=1",
        "ratelimiter.key-strategy=ip"
})
class RateLimitFilterIntegrationTest extends AbstractRedisIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void allowsRequestsUpToCapacityThenReturns429WithHeaders() {
        for (int i = 0; i < 5; i++) {
            ResponseEntity<String> response = restTemplate.getForEntity(url("/api/v1/resource"), String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getHeaders().getFirst("X-RateLimit-Remaining")).isNotNull();
        }

        ResponseEntity<String> throttled = restTemplate.getForEntity(url("/api/v1/resource"), String.class);
        assertThat(throttled.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(throttled.getHeaders().getFirst("Retry-After")).isNotNull();
        assertThat(throttled.getBody()).contains("RATE_LIMIT_EXCEEDED");
    }

    @Test
    void healthAndLimiterStatusEndpointsAreNeverThrottled() {
        for (int i = 0; i < 20; i++) {
            ResponseEntity<String> health = restTemplate.getForEntity(url("/actuator/health"), String.class);
            assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Test
    void perEndpointOverrideAppliesIndependentlyOfGlobalDefault() {
        // /api/v1/orders is annotated with @RateLimit(capacity = 20, refillRate = 5), which is
        // larger than the global default of 5 configured for this test — proving the override
        // mechanism takes precedence rather than falling back to the global bucket.
        for (int i = 0; i < 10; i++) {
            ResponseEntity<String> response = restTemplate.getForEntity(url("/api/v1/orders"), String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }
}
