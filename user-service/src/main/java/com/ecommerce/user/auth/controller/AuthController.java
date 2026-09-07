package com.ecommerce.user.auth.controller;

import com.ecommerce.user.auth.dto.AuthResponse;
import com.ecommerce.user.auth.dto.LoginRequest;
import com.ecommerce.user.auth.dto.RegisterRequest;
import com.ecommerce.user.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for authentication endpoints — public (no auth required). */
@Tag(name = "auth", description = "Public authentication endpoints")
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
    @Operation(summary = "Register a new user account")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Registered, tokens issued"),
        @ApiResponse(
                responseCode = "400",
                description = "Validation failed",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "409", description = "Email already registered")
    })
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
    @Operation(summary = "Authenticate and receive tokens")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Authenticated, tokens issued"),
        @ApiResponse(
                responseCode = "404",
                description = "Unknown email or wrong password",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
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
    @Operation(summary = "Issue a new access token from a refresh token")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "New access token issued"),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid or expired refresh token",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/refresh")
    public AuthResponse refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        return authService.refreshToken(refreshToken);
    }
}
