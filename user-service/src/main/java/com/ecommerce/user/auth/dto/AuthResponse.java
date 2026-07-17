package com.ecommerce.user.auth.dto;

/**
 * Authentication response containing JWT tokens.
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType
) {
    public static AuthResponse of(String accessToken, String refreshToken) {
        return new AuthResponse(accessToken, refreshToken, "Bearer");
    }
}
