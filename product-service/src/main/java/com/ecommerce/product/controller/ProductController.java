package com.ecommerce.product.controller;

import com.ecommerce.product.domain.Product;
import com.ecommerce.product.dto.CreateProductRequest;
import com.ecommerce.product.dto.ProductResponse;
import com.ecommerce.product.exception.ImageUploadException;
import com.ecommerce.product.mapper.ProductMapper;
import com.ecommerce.product.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * REST controller for product catalog management — delegates all business logic to {@link ProductService}.
 */
@RestController
@RequestMapping("/api/products")
@Validated
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);

    private final ProductService productService;
    private final ProductMapper productMapper;

    public ProductController(ProductService productService, ProductMapper productMapper) {
        this.productService = productService;
        this.productMapper = productMapper;
    }

    /**
     * Creates a new product.
     *
     * @param request the validated product payload
     * @return the created product
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createProduct(@Valid @RequestBody CreateProductRequest request) {
        Product product = productMapper.toEntity(request);
        Product saved = productService.createProduct(product);
        return productMapper.toResponse(saved);
    }

    /**
     * Retrieves a product by ID.
     *
     * @param id the product identifier
     * @return the product with a fresh pre-signed image URL if present
     */
    @GetMapping("/{id}")
    public ProductResponse getProduct(@Positive @PathVariable Long id) {
        Product product = productService.getProduct(id);
        ProductResponse response = productMapper.toResponse(product);
        return withPresignedUrl(response, product.getImageUrl());
    }

    /**
     * Lists all products in the catalog.
     *
     * @return list of all products with pre-signed URLs
     */
    @GetMapping
    public List<ProductResponse> getAllProducts() {
        return productService.getAllProducts().stream()
                .map(product -> {
                    ProductResponse resp = productMapper.toResponse(product);
                    return withPresignedUrl(resp, product.getImageUrl());
                })
                .toList();
    }

    private ProductResponse withPresignedUrl(ProductResponse response, String storedKey) {
        String presigned = productService.resolveImageUrl(storedKey);
        if (presigned == null || presigned.equals(response.imageUrl())) {
            return response;
        }
        return new ProductResponse(
                response.id(),
                response.name(),
                response.description(),
                response.price(),
                response.stockQuantity(),
                presigned
        );
    }

    /**
     * Uploads an image for the specified product and returns the pre-signed S3 URL.
     * Validates file type/size via {@link com.ecommerce.product.service.S3Service}.
     *
     * @param id   the product identifier
     * @param file the multipart image file
     * @return JSON with pre-signed URL
     */
    @PostMapping(value = "/{id}/image", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, String>> uploadImage(@Positive @PathVariable Long id,
                                                          @RequestParam("file") MultipartFile file) {
        try {
            String imageUrl = productService.uploadProductImage(id, file);
            return ResponseEntity.ok(Map.of("imageUrl", imageUrl));
        } catch (IOException e) {
            log.error("image_upload_failed product_id={}", id, e);
            throw new ImageUploadException("Failed to upload image", e);
        }
    }
}
