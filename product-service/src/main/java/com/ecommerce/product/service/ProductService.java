package com.ecommerce.product.service;

import com.ecommerce.product.domain.Product;
import com.ecommerce.product.exception.ProductNotFoundException;
import com.ecommerce.product.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Handles product-related business logic with Redis caching.
 */
@Service
@CacheConfig(cacheNames = "products")
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * Retrieves a product by ID. Results are cached in Redis.
     *
     * @param id the product identifier
     * @return the matching product
     * @throws ProductNotFoundException if no product exists with the given ID
     */
    @Cacheable(key = "#id")
    public Product getProduct(Long id) {
        log.info("get_product id={}", id);
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    /**
     * Creates a new product and updates the cache with the result.
     *
     * @param product the product to create
     * @return the persisted product with generated ID
     */
    @CachePut(key = "#result.id")
    public Product createProduct(Product product) {
        log.info("create_product name={}", product.getName());
        return productRepository.save(product);
    }
}
