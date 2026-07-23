package com.ecommerce.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Individual item request within a {@link CreateOrderRequest}.
 */
public record OrderItemRequest(
        @NotNull Long productId,
        @NotBlank String productName,
        @Min(1) int quantity,
        @DecimalMin(value = "0.0", inclusive = false) BigDecimal unitPrice
) {
}
