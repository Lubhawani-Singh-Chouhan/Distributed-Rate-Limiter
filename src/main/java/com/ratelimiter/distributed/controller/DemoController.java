package com.ratelimiter.distributed.controller;

import com.ratelimiter.distributed.annotation.RateLimit;
import com.ratelimiter.distributed.model.CreateOrderRequest;
import com.ratelimiter.distributed.model.OrderDto;
import com.ratelimiter.distributed.model.ResourceDto;
import com.ratelimiter.distributed.util.InstanceIdentity;
import jakarta.validation.Valid;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Purely a demonstration surface for the rate limiter — every request here is protected
 * by {@link com.ratelimiter.distributed.filter.RateLimitFilter} (global default config,
 * or the {@code @RateLimit} override on {@code /api/v1/orders} below). There is no real
 * business logic; responses are mocked and echo back which instance served the request so
 * that a curl loop through nginx can visually confirm load balancing + a shared global limit.
 */
@RestController
@RequestMapping("/api/v1")
public class DemoController {

    private final AtomicInteger orderSequence = new AtomicInteger();

    @GetMapping("/resource")
    public ResponseEntity<ResourceDto> getResource() {
        ResourceDto resource = ResourceDto.builder()
                .id(UUID.randomUUID().toString())
                .name("sample-resource")
                .servedByInstance(InstanceIdentity.current())
                .build();
        return ResponseEntity.ok(resource);
    }

    @GetMapping("/resource/{id}")
    public ResponseEntity<ResourceDto> getResourceById(@PathVariable String id) {
        ResourceDto resource = ResourceDto.builder()
                .id(id)
                .name("sample-resource")
                .servedByInstance(InstanceIdentity.current())
                .build();
        return ResponseEntity.ok(resource);
    }

    // Demonstrates the per-endpoint override mechanism: this endpoint gets a tighter
    // bucket than the global default, regardless of what application.yml says.
    @RateLimit(capacity = 20, refillRate = 5)
    @PostMapping("/orders")
    public ResponseEntity<OrderDto> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        OrderDto order = OrderDto.builder()
                .orderId("ORD-" + orderSequence.incrementAndGet())
                .status("ACCEPTED")
                .servedByInstance(InstanceIdentity.current())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    @RateLimit(capacity = 20, refillRate = 5)
    @GetMapping("/orders")
    public ResponseEntity<OrderDto> getLatestOrder() {
        OrderDto order = OrderDto.builder()
                .orderId("ORD-" + orderSequence.get())
                .status("ACCEPTED")
                .servedByInstance(InstanceIdentity.current())
                .build();
        return ResponseEntity.ok(order);
    }
}
