package com.ecommerce.user.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Utility for generating and validating JSON Web Tokens.
 * The signing key is derived from the JWT_SECRET environment variable.
 */
@Component
public class JwtUtil {

    private final SecretKey signingKey;
    private final long accessExpiration;
    private final long refreshExpiration;

    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.access-expiration-ms}") long accessExpiration,
                   @Value("${app.jwt.refresh-expiration-ms}") long refreshExpiration) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpiration = accessExpiration;
        this.refreshExpiration = refreshExpiration;
    }

    /**
     * Generates a short-lived access token.
     *
     * @param userId the authenticated user's ID
     * @param email  the authenticated user's email
     * @param role   the authenticated user's role
     * @return a signed JWT access token
     */
    public String generateAccessToken(Long userId, String email, String role) {
        return buildToken(userId, email, role, accessExpiration);
    }

    /**
     * Generates a long-lived refresh token.
     *
     * @param userId the authenticated user's ID
     * @param email  the authenticated user's email
     * @param role   the authenticated user's role
     * @return a signed JWT refresh token
     */
    public String generateRefreshToken(Long userId, String email, String role) {
        return buildToken(userId, email, role, refreshExpiration);
    }

    /**
     * Validates the token signature and expiration date.
     *
     * @param token the JWT to validate
     * @return true if the token is valid
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extracts the user ID claim from a token.
     *
     * @param token the JWT
     * @return the user ID
     */
    public Long getUserId(String token) {
        return parseClaims(token).get("userId", Long.class);
    }

    /**
     * Extracts the email claim from a token.
     *
     * @param token the JWT
     * @return the user's email
     */
    public String getEmail(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Extracts the role claim from a token.
     *
     * @param token the JWT
     * @return the user's role
     */
    public String getRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    private String buildToken(Long userId, String email, String role, long expiration) {
        Date now = new Date();
        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration))
                .signWith(signingKey)
                .compact();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
