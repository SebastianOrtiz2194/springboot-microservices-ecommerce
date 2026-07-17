package com.ecommerce.user.auth.service;

import com.ecommerce.user.auth.JwtUtil;
import com.ecommerce.user.auth.dto.AuthResponse;
import com.ecommerce.user.auth.dto.LoginRequest;
import com.ecommerce.user.auth.dto.RegisterRequest;
import com.ecommerce.user.domain.User;
import com.ecommerce.user.exception.UserNotFoundException;
import com.ecommerce.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Handles authentication logic: registration, login, and token refresh.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Registers a new user with encrypted password.
     *
     * @param request the registration payload
     * @return JWT tokens for the new user
     */
    public AuthResponse register(RegisterRequest request) {
        log.info("register_user email={}", request.email());

        User user = new User(
                request.name(),
                request.email(),
                passwordEncoder.encode(request.password()),
                "USER"
        );

        User saved = userRepository.save(user);
        return generateTokens(saved);
    }

    /**
     * Authenticates a user with email and password.
     *
     * @param request the login payload
     * @return JWT tokens
     * @throws UserNotFoundException if the user does not exist or password is wrong
     */
    public AuthResponse login(LoginRequest request) {
        log.info("login_user email={}", request.email());

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UserNotFoundException(request.email()));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new UserNotFoundException(request.email());
        }

        return generateTokens(user);
    }

    /**
     * Issues a new access token from a valid refresh token.
     *
     * @param refreshToken the refresh token
     * @return a new access token
     */
    public AuthResponse refreshToken(String refreshToken) {
        if (!jwtUtil.validateToken(refreshToken)) {
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        Long userId = jwtUtil.getUserId(refreshToken);
        String email = jwtUtil.getEmail(refreshToken);
        String role = jwtUtil.getRole(refreshToken);

        String newAccessToken = jwtUtil.generateAccessToken(userId, email, role);
        return new AuthResponse(newAccessToken, refreshToken, "Bearer");
    }

    private AuthResponse generateTokens(User user) {
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getEmail(), user.getRole());
        return AuthResponse.of(accessToken, refreshToken);
    }
}
