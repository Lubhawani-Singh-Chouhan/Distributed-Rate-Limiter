package com.ratelimiter.distributed.service;

import com.ratelimiter.distributed.annotation.RateLimit;
import com.ratelimiter.distributed.config.RateLimiterProperties;
import com.ratelimiter.distributed.model.RateLimitRule;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Merges the three levels of rate-limit configuration described in the spec, in order
 * of precedence:
 *
 * <ol>
 *   <li>{@code @RateLimit} annotation on the resolved controller method (most specific)</li>
 *   <li>{@code ratelimiter.endpoints["/path"]} override in application.yml</li>
 *   <li>{@code ratelimiter.default-capacity} / {@code default-refill-rate} (global fallback)</li>
 * </ol>
 */
@Slf4j
@Service
public class RateLimitRuleResolver {

    public static final String DEFAULT_SCOPE = "default";

    private final RateLimiterProperties properties;
    private final HandlerMapping handlerMapping;

    // Explicit constructor (rather than @RequiredArgsConstructor) so the @Qualifier is
    // guaranteed to land on the constructor parameter Spring actually injects: Spring MVC
    // registers ~10 HandlerMapping beans (static resources, actuator, etc.), and only
    // "requestMappingHandlerMapping" resolves plain @RequestMapping-annotated controllers.
    public RateLimitRuleResolver(RateLimiterProperties properties,
                                  @Qualifier("requestMappingHandlerMapping") HandlerMapping handlerMapping) {
        this.properties = properties;
        this.handlerMapping = handlerMapping;
    }

    public RateLimitRule resolve(HttpServletRequest request) {
        RateLimit annotation = resolveAnnotation(request);
        if (annotation != null) {
            String scope = "endpoint:" + request.getMethod() + ":" + request.getRequestURI();
            return RateLimitRule.of(annotation.capacity(), annotation.refillRate(), annotation.requestedTokens(), scope);
        }

        RateLimiterProperties.EndpointOverride override = properties.getEndpoints().get(request.getRequestURI());
        if (override != null) {
            long capacity = override.getCapacity() != null ? override.getCapacity() : properties.getDefaultCapacity();
            long refillRate = override.getRefillRate() != null ? override.getRefillRate() : properties.getDefaultRefillRate();
            String scope = "endpoint:" + request.getRequestURI();
            return RateLimitRule.of(capacity, refillRate, 1L, scope);
        }

        return RateLimitRule.of(properties.getDefaultCapacity(), properties.getDefaultRefillRate(), 1L, DEFAULT_SCOPE);
    }

    private RateLimit resolveAnnotation(HttpServletRequest request) {
        try {
            HandlerExecutionChain chain = handlerMapping.getHandler(request);
            if (chain == null || !(chain.getHandler() instanceof HandlerMethod handlerMethod)) {
                return null;
            }
            return handlerMethod.getMethodAnnotation(RateLimit.class);
        } catch (Exception e) {
            // Handler resolution failing (e.g. for a path with no mapping) just means "no override
            // available"; the request will still get a default-config decision, and any resulting
            // 404 is produced normally further down the chain.
            log.trace("Could not resolve handler for {} to check @RateLimit: {}", request.getRequestURI(), e.getMessage());
            return null;
        }
    }
}
