package com.ecommerce.order.dto;

import java.math.BigDecimal;

/**
 * Individual item within an {@link OrderResponse}.
 */
public record OrderItemResponse(
        Long id,
        Long productId,
        String productName,
        int quantity,
        BigDecimal unitPrice
) {
}
