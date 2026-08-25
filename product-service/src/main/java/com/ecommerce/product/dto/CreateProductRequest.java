package com.ecommerce.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Request payload for creating a new product. */
public record CreateProductRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 1000) String description,
        @NotNull @Positive @DecimalMin(value = "0.0", inclusive = false) BigDecimal price,
        @Min(0) Integer stockQuantity) {}
