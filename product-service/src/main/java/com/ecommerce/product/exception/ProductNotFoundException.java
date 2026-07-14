package com.ecommerce.product.exception;

/**
 * Thrown when a requested product cannot be found.
 */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(Long id) {
        super("Product not found with id: " + id);
    }
}
