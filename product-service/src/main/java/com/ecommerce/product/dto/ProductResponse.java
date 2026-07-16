package com.ecommerce.product.dto;

import java.math.BigDecimal;

/**
 * Product data returned to API clients.
 */
public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        Integer stockQuantity,
        String imageUrl
) {
}
