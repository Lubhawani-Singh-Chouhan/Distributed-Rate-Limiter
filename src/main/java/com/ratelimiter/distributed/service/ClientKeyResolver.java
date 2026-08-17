package com.ratelimiter.distributed.service;

import com.ratelimiter.distributed.config.RateLimiterProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Derives the logical "client" identity a bucket is keyed on, per the configured
 * {@link RateLimiterProperties.KeyStrategy}.
 */
@Service
@RequiredArgsConstructor
public class ClientKeyResolver {

    private static final String ANONYMOUS = "anonymous";

    private final RateLimiterProperties properties;

    public String resolve(HttpServletRequest request) {
        return switch (properties.getKeyStrategy()) {
            case API_KEY -> resolveApiKey(request);
            case USER_ID -> resolveHeaderOrFallback(request, "X-User-Id");
            case IP -> resolveClientIp(request);
        };
    }

    private String resolveApiKey(HttpServletRequest request) {
        String apiKey = request.getHeader(properties.getApiKeyHeader());
        return StringUtils.hasText(apiKey) ? apiKey : resolveClientIp(request);
    }

    private String resolveHeaderOrFallback(HttpServletRequest request, String header) {
        String value = request.getHeader(header);
        return StringUtils.hasText(value) ? value : resolveClientIp(request);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            // Left-most entry is the original client when the header is a comma-separated chain.
            return forwardedFor.split(",")[0].trim();
        }
        String remoteAddr = request.getRemoteAddr();
        return StringUtils.hasText(remoteAddr) ? remoteAddr : ANONYMOUS;
    }
}
