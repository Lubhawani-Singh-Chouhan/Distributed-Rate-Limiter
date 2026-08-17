package com.ratelimiter.distributed.controller;

import com.ratelimiter.distributed.config.RateLimiterProperties;
import com.ratelimiter.distributed.model.BucketSnapshot;
import com.ratelimiter.distributed.service.RateLimitRuleResolver;
import com.ratelimiter.distributed.service.TokenBucketService;
import com.ratelimiter.distributed.util.InstanceIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Debug/demo endpoint for inspecting a client's bucket state without consuming a token.
 * Intentionally excluded from rate limiting (see {@code ratelimiter.excluded-paths}) so it
 * can always be used to observe the system, even for a client that is currently throttled.
 */
@RestController
@RequestMapping("/api/v1/limiter")
@RequiredArgsConstructor
public class LimiterStatusController {

    private final TokenBucketService tokenBucketService;
    private final RateLimiterProperties properties;

    @GetMapping("/status")
    public ResponseEntity<BucketSnapshot> status(@RequestParam String clientKey) {
        // Mirrors the bucket identity the filter uses for non-overridden ("default scope")
        // endpoints, so this reflects the same global bucket a client is actually spending
        // down when calling any endpoint that doesn't have its own @RateLimit/config override.
        String bucketIdentity = clientKey + ":" + RateLimitRuleResolver.DEFAULT_SCOPE;
        BucketSnapshot snapshot = tokenBucketService.peek(
                bucketIdentity, properties.getDefaultCapacity(), properties.getDefaultRefillRate());

        BucketSnapshot displaySnapshot = BucketSnapshot.builder()
                .clientKey(clientKey)
                .exists(snapshot.isExists())
                .tokens(snapshot.getTokens())
                .capacity(snapshot.getCapacity())
                .refillRatePerSecond(snapshot.getRefillRatePerSecond())
                .lastRefillEpochMs(snapshot.getLastRefillEpochMs())
                .build();
        return ResponseEntity.ok(displaySnapshot);
    }

    @GetMapping("/instance")
    public ResponseEntity<String> instance() {
        return ResponseEntity.ok(InstanceIdentity.current());
    }
}
