package com.ratelimiter.distributed.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratelimiter.distributed.filter.RateLimitFilter;
import com.ratelimiter.distributed.service.ClientKeyResolver;
import com.ratelimiter.distributed.service.RateLimitRuleResolver;
import com.ratelimiter.distributed.service.TokenBucketService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers {@link RateLimitFilter} at the very front of the servlet filter chain.
 * {@link Ordered#HIGHEST_PRECEDENCE} guarantees it runs before Spring Security's
 * {@code FilterChainProxy} (or any other security filter) in a deployment that has one,
 * satisfying the "before auth, if any" requirement without this module needing a
 * compile-time dependency on Spring Security.
 */
@Configuration
public class WebConfig {

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
            TokenBucketService tokenBucketService,
            RateLimitRuleResolver ruleResolver,
            ClientKeyResolver clientKeyResolver,
            RateLimiterProperties properties,
            ObjectMapper objectMapper) {

        RateLimitFilter filter = new RateLimitFilter(tokenBucketService, ruleResolver, clientKeyResolver, properties, objectMapper);

        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("rateLimitFilter");
        return registration;
    }
}
