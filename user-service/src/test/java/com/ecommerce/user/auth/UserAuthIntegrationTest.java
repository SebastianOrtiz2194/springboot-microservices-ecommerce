package com.ecommerce.user.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecommerce.user.auth.dto.AuthResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end integration test of the auth flow against a real PostgreSQL container: register →
 * login → refresh → access a protected endpoint with the issued token.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class UserAuthIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private TestRestTemplate restTemplate;

    @Test
    void registerLoginRefreshAndAccessProtectedEndpoint() {
        // 1. Register a new user -> access + refresh tokens
        ResponseEntity<AuthResponse> register =
                restTemplate.postForEntity(
                        "/api/auth/register",
                        Map.of(
                                "name",
                                "Integration User",
                                "email",
                                "integration@example.com",
                                "password",
                                "s3cret-password"),
                        AuthResponse.class);

        assertThat(register.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(register.getBody()).isNotNull();
        assertThat(register.getBody().accessToken()).isNotBlank();
        assertThat(register.getBody().refreshToken()).isNotBlank();
        assertThat(register.getBody().tokenType()).isEqualTo("Bearer");

        String accessToken = register.getBody().accessToken();
        String refreshToken = register.getBody().refreshToken();

        // 2. Access a protected endpoint with the access token
        ResponseEntity<java.util.List> users =
                restTemplate.exchange(
                        "/api/users",
                        HttpMethod.GET,
                        new HttpEntity<>(bearerHeaders(accessToken)),
                        java.util.List.class);

        assertThat(users.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(users.getBody())
                .anySatisfy(
                        u ->
                                assertThat(((Map<?, ?>) u).get("email"))
                                        .isEqualTo("integration@example.com"));

        // 3. Login with the same credentials -> fresh tokens
        ResponseEntity<AuthResponse> login =
                restTemplate.postForEntity(
                        "/api/auth/login",
                        Map.of("email", "integration@example.com", "password", "s3cret-password"),
                        AuthResponse.class);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody()).isNotNull();

        // 4. Refresh -> new access token works against the protected endpoint
        ResponseEntity<AuthResponse> refresh =
                restTemplate.postForEntity(
                        "/api/auth/refresh",
                        Map.of("refreshToken", refreshToken),
                        AuthResponse.class);

        assertThat(refresh.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refresh.getBody()).isNotNull();
        String newAccessToken = refresh.getBody().accessToken();
        assertThat(newAccessToken).isNotBlank().isNotEqualTo(accessToken);

        ResponseEntity<Map> meAgain =
                restTemplate.exchange(
                        "/api/users/1",
                        HttpMethod.GET,
                        new HttpEntity<>(bearerHeaders(newAccessToken)),
                        Map.class);
        assertThat(meAgain.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 5. Wrong password -> 404 (user not found semantics)
        ResponseEntity<Map> badLogin =
                restTemplate.postForEntity(
                        "/api/auth/login",
                        Map.of("email", "integration@example.com", "password", "wrong-password"),
                        Map.class);
        assertThat(badLogin.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void protectedEndpointRejectsRequestWithoutToken() {
        ResponseEntity<Map> anonymous =
                restTemplate.exchange("/api/users/1", HttpMethod.GET, HttpEntity.EMPTY, Map.class);

        assertThat(anonymous.getStatusCode().is4xxClientError()).isTrue();
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
