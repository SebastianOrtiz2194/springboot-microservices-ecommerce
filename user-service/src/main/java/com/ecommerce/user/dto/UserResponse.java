package com.ecommerce.user.dto;

/**
 * User data returned to API clients. Excludes sensitive fields like password.
 */
public record UserResponse(
        Long id,
        String name,
        String email,
        String role
) {
}
