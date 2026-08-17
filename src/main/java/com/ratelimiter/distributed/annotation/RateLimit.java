package com.ratelimiter.distributed.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Overrides the bucket parameters used by {@link com.ratelimiter.distributed.filter.RateLimitFilter}
 * for the annotated controller method, taking precedence over any {@code ratelimiter.endpoints}
 * config-map entry and the global {@code ratelimiter.default-*} values.
 *
 * <pre>
 * {@literal @}RateLimit(capacity = 100, refillRate = 20)
 * {@literal @}GetMapping("/api/v1/orders")
 * public List&lt;Order&gt; listOrders() { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** Max burst size (tokens) for this endpoint. */
    long capacity();

    /** Tokens refilled per second for this endpoint. */
    long refillRate();

    /** Tokens a single request to this endpoint consumes. */
    long requestedTokens() default 1L;
}
