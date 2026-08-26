package com.ecommerce.user.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.user.auth.JwtUtil;
import com.ecommerce.user.auth.dto.AuthResponse;
import com.ecommerce.user.auth.dto.LoginRequest;
import com.ecommerce.user.auth.dto.RegisterRequest;
import com.ecommerce.user.domain.User;
import com.ecommerce.user.exception.UserNotFoundException;
import com.ecommerce.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Unit tests for {@link AuthService}. */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;

    @Mock private PasswordEncoder passwordEncoder;

    @Mock private JwtUtil jwtUtil;

    @InjectMocks private AuthService authService;

    @Test
    void register_hashesPasswordAndIssuesTokens() {
        RegisterRequest request = new RegisterRequest("Alice", "alice@example.com", "secret123");
        when(passwordEncoder.encode("secret123")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class)))
                .thenAnswer(
                        inv -> {
                            User saved = inv.getArgument(0);
                            saved.setId(1L);
                            return saved;
                        });
        when(jwtUtil.generateAccessToken(1L, "alice@example.com", "USER"))
                .thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(1L, "alice@example.com", "USER"))
                .thenReturn("refresh-token");

        AuthResponse response = authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getPassword()).isEqualTo("hashed-password");
        assertThat(savedUser.getRole()).isEqualTo("USER");
        assertThat(savedUser.getPassword()).isNotEqualTo("secret123");

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }

    @Test
    void login_returnsTokensWhenCredentialsMatch() {
        LoginRequest request = new LoginRequest("alice@example.com", "secret123");
        User user = new User("Alice", "alice@example.com", "hashed-password", "USER");
        user.setId(1L);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "hashed-password")).thenReturn(true);
        when(jwtUtil.generateAccessToken(1L, "alice@example.com", "USER"))
                .thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(1L, "alice@example.com", "USER"))
                .thenReturn("refresh-token");

        AuthResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void login_throwsWhenUserNotFound() {
        LoginRequest request = new LoginRequest("nobody@example.com", "secret123");
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("nobody@example.com");
    }

    @Test
    void login_throwsWhenPasswordDoesNotMatch() {
        LoginRequest request = new LoginRequest("alice@example.com", "wrong-password");
        User user = new User("Alice", "alice@example.com", "hashed-password", "USER");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UserNotFoundException.class);

        verify(jwtUtil, never()).generateAccessToken(any(), any(), any());
    }

    @Test
    void refreshToken_throwsWhenTokenInvalid() {
        when(jwtUtil.validateToken("expired-token")).thenReturn(false);

        assertThatThrownBy(() -> authService.refreshToken("expired-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired refresh token");
    }

    @Test
    void refreshToken_issuesNewAccessToken() {
        when(jwtUtil.validateToken("valid-refresh")).thenReturn(true);
        when(jwtUtil.getUserId("valid-refresh")).thenReturn(1L);
        when(jwtUtil.getEmail("valid-refresh")).thenReturn("alice@example.com");
        when(jwtUtil.getRole("valid-refresh")).thenReturn("USER");
        when(jwtUtil.generateAccessToken(1L, "alice@example.com", "USER"))
                .thenReturn("new-access-token");

        AuthResponse response = authService.refreshToken("valid-refresh");

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("valid-refresh");
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }
}
