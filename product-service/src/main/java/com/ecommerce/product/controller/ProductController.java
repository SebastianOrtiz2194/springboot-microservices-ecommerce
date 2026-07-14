package com.ecommerce.product.controller;

import com.ecommerce.product.domain.Product;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.product.service.ProductService;
import com.ecommerce.product.service.S3Service;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * REST controller for product catalog management, including image uploads.
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);

    private final ProductRepository productRepository;
    private final S3Service s3Service;
    private final ProductService productService;

    public ProductController(ProductRepository productRepository,
                             S3Service s3Service,
                             ProductService productService) {
        this.productRepository = productRepository;
        this.s3Service = s3Service;
        this.productService = productService;
    }

    /**
     * Creates a new product and evicts the all-products cache.
     *
     * @param product the validated product payload
     * @return the created product with HTTP 201
     */
    @PostMapping
    @CacheEvict(value = "products", allEntries = true)
    public ResponseEntity<Product> createProduct(@Valid @RequestBody Product product) {
        Product savedProduct = productRepository.save(product);
        return new ResponseEntity<>(savedProduct, HttpStatus.CREATED);
    }

    /**
     * Retrieves a product by ID. Result is cached in Redis per product ID.
     *
     * @param id the product identifier
     * @return the product if found, otherwise HTTP 404
     */
    @GetMapping("/{id}")
    @Cacheable(value = "products", key = "#id")
    public ResponseEntity<Product> getProduct(@PathVariable Long id) {
        Optional<Product> product = productRepository.findById(id);
        return product.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Lists all products. Results are cached in Redis.
     *
     * @return list of all products in the catalog
     */
    @GetMapping
    @Cacheable(value = "products")
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    /**
     * Uploads an image for the specified product and returns the pre-signed S3 URL.
     *
     * @param id   the product identifier
     * @param file the multipart image file
     * @return the pre-signed URL for the uploaded image
     */
    @PostMapping("/{id}/image")
    public ResponseEntity<String> uploadImage(@PathVariable Long id,
                                              @RequestParam("file") MultipartFile file) {
        try {
            Optional<Product> productOpt = productRepository.findById(id);
            if (productOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Product product = productOpt.get();
            String imageUrl = s3Service.uploadImage(file);
            product.setImageUrl(imageUrl);
            productRepository.save(product);

            return ResponseEntity.ok(imageUrl);
        } catch (IOException e) {
            log.error("image_upload_failed product_id={}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to upload image: " + e.getMessage());
        }
    }
}
