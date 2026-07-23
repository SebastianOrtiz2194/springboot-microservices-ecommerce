package com.ecommerce.product.event;

import java.math.BigDecimal;

/**
 * Consumed from the order-placed Kafka topic to update stock.
 */
public record OrderPlacedEvent(
        Long orderId,
        Long userId,
        java.util.List<OrderItemEvent> items,
        BigDecimal totalAmount,
        java.time.LocalDateTime createdAt
) {
}
