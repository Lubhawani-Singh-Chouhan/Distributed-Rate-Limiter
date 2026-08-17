package com.ratelimiter.distributed.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** JSON body returned alongside a 429 response. */
@Getter
@Builder
@AllArgsConstructor
public class RateLimitErrorResponse {
    private final String error;
    private final String message;
    private final long retryAfterMs;
    private final long remainingTokens;
    private final int status;
}
