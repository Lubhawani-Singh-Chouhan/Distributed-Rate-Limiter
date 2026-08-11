package com.ratelimiter.distributed.exception;

import com.ratelimiter.distributed.model.RateLimitResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Translates rate-limiting (and other) exceptions into clean JSON error responses.
 * Also used by {@link com.ratelimiter.distributed.filter.RateLimitFilter} as a source
 * of truth for how a 429 body/headers should look, so both the annotation-driven
 * (AOP) path and the filter path produce identical responses.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<RateLimitErrorResponse> handleRateLimitExceeded(RateLimitExceededException ex) {
        RateLimitResult result = ex.getResult();

        HttpHeaders headers = new HttpHeaders();
        headers.add("Retry-After", String.valueOf(Math.max(0, result.getRetryAfterMs()) / 1000));
        headers.add("X-RateLimit-Limit", String.valueOf(result.getCapacity()));
        headers.add("X-RateLimit-Remaining", String.valueOf(result.getRemainingTokens()));

        RateLimitErrorResponse body = RateLimitErrorResponse.builder()
                .error("RATE_LIMIT_EXCEEDED")
                .message(ex.getMessage())
                .retryAfterMs(result.getRetryAfterMs())
                .remainingTokens(result.getRemainingTokens())
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .build();

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).headers(headers).body(body);
    }
}
