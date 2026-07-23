package com.ecommerce.order.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Published to Kafka when an order is created.
 * Consumed by product-service to decrement stock.
 */
public record OrderPlacedEvent(
        Long orderId,
        Long userId,
        List<OrderItemEvent> items,
        BigDecimal totalAmount,
        LocalDateTime createdAt
) {
}
