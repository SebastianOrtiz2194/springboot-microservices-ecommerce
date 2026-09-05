package com.ecommerce.product.service;

import com.ecommerce.product.domain.Product;
import com.ecommerce.product.exception.ProductNotFoundException;
import com.ecommerce.product.repository.ProductRepository;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Handles product-related business logic with Redis caching. Uses separate caches for single
 * product and product lists to avoid key collisions and stale data after stock/image updates.
 */
@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final S3Service s3Service;

    public ProductService(ProductRepository productRepository, S3Service s3Service) {
        this.productRepository = productRepository;
        this.s3Service = s3Service;
    }

    /**
     * Retrieves a product by ID. Results are cached in Redis under 'product' cache. Guarded by a
     * bulkhead so slow downstream calls cannot exhaust all request threads.
     *
     * @param id the product identifier
     * @return the matching product
     * @throws ProductNotFoundException if no product exists with the given ID
     */
    @Bulkhead(name = "productLookup")
    @Cacheable(cacheNames = "product", key = "#id", unless = "#result == null")
    public Product getProduct(Long id) {
        log.info("get_product id={}", id);
        return productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    /**
     * Creates a new product and evicts the product list cache.
     *
     * @param product the product to create
     * @return the persisted product with generated ID
     */
    @Transactional
    @CacheEvict(
            cacheNames = {"product", "productList"},
            allEntries = true)
    public Product createProduct(Product product) {
        log.info("create_product name={}", product.getName());
        return productRepository.save(product);
    }

    /**
     * Returns all products in the catalog. Results are cached in Redis under 'productList'. Uses a
     * short TTL (10m via RedisCacheConfig) to limit stale data.
     *
     * @return list of all products
     * @deprecated Use {@link #getAllProducts(Pageable)} for pagination; this loads entire table.
     */
    @Cacheable(cacheNames = "productList")
    public List<Product> getAllProducts() {
        log.info("get_all_products");
        return productRepository.findAll();
    }

    /**
     * Returns a paginated product catalog.
     *
     * @param pageable pagination information
     * @return page of products
     */
    public Page<Product> getAllProducts(Pageable pageable) {
        log.info(
                "get_all_products_paginated page={} size={}",
                pageable.getPageNumber(),
                pageable.getPageSize());
        return productRepository.findAll(pageable);
    }

    /**
     * Uploads an image for a product, stores the S3 key, and returns a pre-signed URL. Evicts
     * cached product and list entries to avoid stale image URLs.
     *
     * @param productId the product identifier
     * @param file the multipart image file
     * @return the pre-signed S3 URL for the uploaded image
     * @throws ProductNotFoundException if the product does not exist
     * @throws IOException if the S3 upload fails
     */
    @Transactional
    @Caching(
            evict = {
                @CacheEvict(cacheNames = "product", key = "#productId"),
                @CacheEvict(cacheNames = "productList", allEntries = true)
            })
    public String uploadProductImage(Long productId, MultipartFile file) throws IOException {
        Product product =
                productRepository
                        .findById(productId)
                        .orElseThrow(() -> new ProductNotFoundException(productId));

        String key = s3Service.uploadImage(file);
        product.setImageUrl(key);
        productRepository.save(product);

        String presignedUrl = s3Service.createPresignedUrl(key);
        log.info("product_image_uploaded product_id={} key={}", productId, key);
        return presignedUrl;
    }

    /**
     * Resolves a stored S3 key to a pre-signed URL for API responses. Returns null if no image is
     * stored; handles legacy presigned URLs.
     *
     * @param storedValue the value persisted in imageUrl column (key or legacy URL)
     * @return presigned URL or null
     */
    public String resolveImageUrl(String storedValue) {
        return s3Service.createPresignedUrl(storedValue);
    }
}
