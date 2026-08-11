package com.ratelimiter.distributed.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderDto {
    private final String orderId;
    private final String status;
    private final String servedByInstance;
}
