package com.ecommerce.product.exception;

/**
 * Thrown when an order requests more stock than available.
 */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(Long productId, int available, int requested) {
        super("Insufficient stock for product " + productId + ": available=" + available + ", requested=" + requested);
    }

    public InsufficientStockException(String message) {
        super(message);
    }
}
