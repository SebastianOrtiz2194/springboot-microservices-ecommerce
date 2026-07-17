package com.ecommerce.user.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for user login.
 */
public record LoginRequest(
        @Email @NotBlank String email,
        @NotBlank String password
) {
}
