package com.ecommerce.product.mapper;

import com.ecommerce.product.domain.Product;
import com.ecommerce.product.dto.CreateProductRequest;
import com.ecommerce.product.dto.ProductResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps between {@link Product} entity and its DTOs.
 */
@Mapper(componentModel = "spring")
public interface ProductMapper {

    ProductResponse toResponse(Product product);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "imageUrl", ignore = true)
    Product toEntity(CreateProductRequest request);
}
