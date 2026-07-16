package com.ecommerce.product.service;

import com.ecommerce.product.domain.Product;
import com.ecommerce.product.exception.ProductNotFoundException;
import com.ecommerce.product.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Handles product-related business logic with Redis caching.
 */
@Service
@CacheConfig(cacheNames = "products")
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final S3Service s3Service;

    public ProductService(ProductRepository productRepository, S3Service s3Service) {
        this.productRepository = productRepository;
        this.s3Service = s3Service;
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
     * Creates a new product and evicts the all-products cache.
     *
     * @param product the product to create
     * @return the persisted product with generated ID
     */
    @CacheEvict(allEntries = true)
    public Product createProduct(Product product) {
        log.info("create_product name={}", product.getName());
        return productRepository.save(product);
    }

    /**
     * Returns all products in the catalog. Results are cached in Redis.
     *
     * @return list of all products
     */
    @Cacheable
    public List<Product> getAllProducts() {
        log.info("get_all_products");
        return productRepository.findAll();
    }

    /**
     * Uploads an image for a product and updates its image URL.
     *
     * @param productId the product identifier
     * @param file      the multipart image file
     * @return the pre-signed S3 URL for the uploaded image
     * @throws ProductNotFoundException if the product does not exist
     * @throws IOException               if the S3 upload fails
     */
    public String uploadProductImage(Long productId, MultipartFile file) throws IOException {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        String imageUrl = s3Service.uploadImage(file);
        product.setImageUrl(imageUrl);
        productRepository.save(product);

        log.info("product_image_uploaded product_id={}", productId);
        return imageUrl;
    }
}
