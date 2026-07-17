package com.ecommerce.user.auth.controller;

import com.ecommerce.user.auth.dto.AuthResponse;
import com.ecommerce.user.auth.dto.LoginRequest;
import com.ecommerce.user.auth.dto.RegisterRequest;
import com.ecommerce.user.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller for authentication endpoints — public (no auth required).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Registers a new user account.
     *
     * @param request the registration payload
     * @return JWT access and refresh tokens
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    /**
     * Authenticates an existing user.
     *
     * @param request the login payload
     * @return JWT access and refresh tokens
     */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /**
     * Issues a new access token from a valid refresh token.
     *
     * @param body a map containing the refreshToken field
     * @return a new access token
     */
    @PostMapping("/refresh")
    public AuthResponse refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        return authService.refreshToken(refreshToken);
    }
}
