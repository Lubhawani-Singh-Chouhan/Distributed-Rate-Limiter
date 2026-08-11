package com.ratelimiter.distributed.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ResourceDto {
    private final String id;
    private final String name;
    private final String servedByInstance;
}
