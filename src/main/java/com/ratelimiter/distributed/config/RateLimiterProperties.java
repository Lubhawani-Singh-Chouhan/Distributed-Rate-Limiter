package com.ratelimiter.distributed.config;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code ratelimiter.*} block from application.yml.
 *
 * <pre>
 * ratelimiter:
 *   default-capacity: 50
 *   default-refill-rate: 10
 *   key-strategy: api-key
 *   endpoints:
 *     "/api/v1/orders":
 *       capacity: 20
 *       refill-rate: 5
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "ratelimiter")
public class RateLimiterProperties {

    /** Max burst size (tokens) when no more specific override applies. */
    private long defaultCapacity = 50;

    /** Tokens added back per second when no more specific override applies. */
    private long defaultRefillRate = 10;

    /** How to derive the client identity used as the bucket key. */
    private KeyStrategy keyStrategy = KeyStrategy.API_KEY;

    /** Header to read the API key from, when keyStrategy = API_KEY. */
    private String apiKeyHeader = "X-API-Key";

    /** Whether the rate limiting filter is active at all. */
    private boolean enabled = true;

    /** Ant-style path patterns that should never be rate limited. */
    private String[] excludedPaths = {
        "/actuator/health", "/actuator/health/**", "/api/v1/limiter/**"
    };

    /** Optional per-endpoint overrides, keyed by request path (exact match). */
    private Map<String, EndpointOverride> endpoints = new HashMap<>();

    public enum KeyStrategy {
        API_KEY,
        IP,
        USER_ID
    }

    @Data
    public static class EndpointOverride {
        private Long capacity;
        private Long refillRate;
    }
}
