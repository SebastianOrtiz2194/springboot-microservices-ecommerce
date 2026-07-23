package com.ecommerce.order.exception;

/**
 * Thrown when a requested order cannot be found.
 */
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(Long id) {
        super("Order not found with id: " + id);
    }
}
