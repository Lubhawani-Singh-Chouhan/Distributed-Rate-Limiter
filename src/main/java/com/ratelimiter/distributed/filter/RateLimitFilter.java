package com.ratelimiter.distributed.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratelimiter.distributed.config.RateLimiterProperties;
import com.ratelimiter.distributed.exception.RateLimitErrorResponse;
import com.ratelimiter.distributed.model.RateLimitResult;
import com.ratelimiter.distributed.model.RateLimitRule;
import com.ratelimiter.distributed.service.ClientKeyResolver;
import com.ratelimiter.distributed.service.RateLimitRuleResolver;
import com.ratelimiter.distributed.service.TokenBucketService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Enforces the token-bucket limit for every incoming request.
 *
 * <p>Registered with a very low order (see {@code WebConfig}) so it runs as early as
 * possible in the servlet filter chain — ahead of authentication/authorization filters,
 * matching the spec's "ordered appropriately (before auth, if any)" requirement. Rejecting
 * abusive traffic before it pays the cost of an auth lookup is both cheaper and reduces the
 * blast radius of credential-stuffing / brute-force traffic hitting the auth layer at all.
 *
 * <p>Deliberately writes the 429 response itself instead of throwing, since exceptions raised
 * this early in the chain run outside of Spring MVC's {@code @ControllerAdvice} machinery.
 */
@Slf4j
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final TokenBucketService tokenBucketService;
    private final RateLimitRuleResolver ruleResolver;
    private final ClientKeyResolver clientKeyResolver;
    private final RateLimiterProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!properties.isEnabled() || isExcluded(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientKey = clientKeyResolver.resolve(request);
        RateLimitRule rule = ruleResolver.resolve(request);
        // Scoping the bucket identity by rule (not just by client) keeps a per-endpoint
        // override's Redis state isolated from the client's global-default bucket and
        // from any other override — otherwise two endpoints with different capacities
        // would fight over the same stored `tokens` value.
        String bucketIdentity = clientKey + ":" + rule.getScope();

        RateLimitResult result = tokenBucketService.tryConsume(
                bucketIdentity, rule.getCapacity(), rule.getRefillRatePerSecond(), rule.getRequestedTokens());

        response.setHeader("X-RateLimit-Limit", String.valueOf(rule.getCapacity()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.getRemainingTokens()));

        if (!result.isAllowed()) {
            rejectWithTooManyRequests(response, result);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void rejectWithTooManyRequests(HttpServletResponse response, RateLimitResult result) throws IOException {
        long retryAfterSeconds = Math.max(0, result.getRetryAfterMs()) / 1000;
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        RateLimitErrorResponse body = RateLimitErrorResponse.builder()
                .error("RATE_LIMIT_EXCEEDED")
                .message("Rate limit exceeded. Try again later.")
                .retryAfterMs(result.getRetryAfterMs())
                .remainingTokens(result.getRemainingTokens())
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .build();

        objectMapper.writeValue(response.getWriter(), body);
    }

    private boolean isExcluded(String requestUri) {
        for (String pattern : properties.getExcludedPaths()) {
            if (PATH_MATCHER.match(pattern, requestUri)) {
                return true;
            }
        }
        return false;
    }
}
