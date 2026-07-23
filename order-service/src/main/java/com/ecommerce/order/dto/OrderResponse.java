package com.ecommerce.order.dto;

import com.ecommerce.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Order data returned to API clients.
 */
public record OrderResponse(
        Long id,
        Long userId,
        BigDecimal totalAmount,
        OrderStatus status,
        LocalDateTime createdAt,
        List<OrderItemResponse> items
) {
}
