package com.ecommerce.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Request payload for creating an order.
 */
public record CreateOrderRequest(
        @NotEmpty @Valid List<OrderItemRequest> items
) {
}
