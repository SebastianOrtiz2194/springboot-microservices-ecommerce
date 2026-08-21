package com.ecommerce.product.exception;

/**
 * Thrown when product image upload to S3 fails.
 */
public class ImageUploadException extends RuntimeException {

    public ImageUploadException(String message, Throwable cause) {
        super(message, cause);
    }

    public ImageUploadException(String message) {
        super(message);
    }
}
