package com.ecommerce.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for creating a new user.
 */
public record CreateUserRequest(
        @NotBlank String name,
        @Email @NotBlank String email
) {
}
