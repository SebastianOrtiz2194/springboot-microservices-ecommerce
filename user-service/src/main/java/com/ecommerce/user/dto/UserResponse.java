package com.ecommerce.user.dto;

/**
 * User data returned to API clients.
 */
public record UserResponse(
        Long id,
        String name,
        String email
) {
}
