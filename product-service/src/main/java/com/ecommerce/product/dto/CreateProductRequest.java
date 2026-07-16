package com.ecommerce.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

/**
 * Request payload for creating a new product.
 */
public record CreateProductRequest(
        @NotBlank String name,
        String description,
        @DecimalMin(value = "0.0", inclusive = false) BigDecimal price,
        Integer stockQuantity
) {
}
