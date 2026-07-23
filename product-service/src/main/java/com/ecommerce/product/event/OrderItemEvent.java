package com.ecommerce.product.event;

import java.math.BigDecimal;

/**
 * Individual item within an {@link OrderPlacedEvent}.
 */
public record OrderItemEvent(
        Long productId,
        int quantity,
        BigDecimal unitPrice
) {
}
