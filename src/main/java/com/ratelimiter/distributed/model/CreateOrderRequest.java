package com.ratelimiter.distributed.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateOrderRequest {

    @NotBlank(message = "sku must not be blank")
    private String sku;

    @Positive(message = "quantity must be positive")
    private int quantity;
}
