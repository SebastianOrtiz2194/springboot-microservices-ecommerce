package com.ecommerce.order.exception;

/** Thrown when an order event cannot be published to Kafka (downstream outage). */
public class OrderEventPublishException extends RuntimeException {

    public OrderEventPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
