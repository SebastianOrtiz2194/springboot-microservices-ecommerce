package com.ecommerce.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

/** Unit tests for {@link JwtAuthenticationGlobalFilter} paths and token handling. */
class JwtAuthenticationFilterTest {

    private static final String SECRET =
            "dev-secret-key-that-is-at-least-32-chars-long-for-local-dev-only-please-change";
    private static final SecretKey KEY =
            Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private final JwtAuthenticationGlobalFilter filter =
            new JwtAuthenticationGlobalFilter(SECRET, new ObjectMapper());

    private static String token(Date expiry, SecretKey key) {
        return Jwts.builder()
                .subject("alice@example.com")
                .claim("userId", 7L)
                .claim("role", "USER")
                .issuedAt(new Date(System.currentTimeMillis() - 60_000))
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    private static Date inOneHour() {
        return new Date(System.currentTimeMillis() + 3_600_000);
    }

    private static Date oneMinuteAgo() {
        return new Date(System.currentTimeMillis() - 60_000);
    }

    private static GatewayFilterChain recordingChain(AtomicBoolean called) {
        return exchange -> {
            called.set(true);
            return Mono.empty();
        };
    }

    @Test
    void protectedPathWithoutToken_isRejectedWithProblemDetail() {
        MockServerWebExchange exchange =
                MockServerWebExchange.from(MockServerHttpRequest.get("/api/products"));
        AtomicBoolean called = new AtomicBoolean(false);

        filter.filter(exchange, recordingChain(called)).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("\"title\":\"Unauthorized\"")
                .contains("Missing bearer token");
        assertThat(called).isFalse();
    }

    @Test
    void malformedToken_isRejected() {
        MockServerWebExchange exchange =
                MockServerWebExchange.from(
                        MockServerHttpRequest.get("/api/orders")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"));
        AtomicBoolean called = new AtomicBoolean(false);

        filter.filter(exchange, recordingChain(called)).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("Invalid or expired token");
        assertThat(called).isFalse();
    }

    @Test
    void tokenSignedWithAnotherKey_isRejected() {
        String forged =
                token(
                        inOneHour(),
                        Keys.hmacShaKeyFor(
                                "another-secret-key-that-is-also-at-least-32-bytes-long!"
                                        .getBytes(StandardCharsets.UTF_8)));
        MockServerWebExchange exchange =
                MockServerWebExchange.from(
                        MockServerHttpRequest.get("/api/orders")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + forged));
        AtomicBoolean called = new AtomicBoolean(false);

        filter.filter(exchange, recordingChain(called)).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(called).isFalse();
    }

    @Test
    void expiredToken_isRejected() {
        MockServerWebExchange exchange =
                MockServerWebExchange.from(
                        MockServerHttpRequest.get("/api/orders")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(oneMinuteAgo(), KEY)));
        AtomicBoolean called = new AtomicBoolean(false);

        filter.filter(exchange, recordingChain(called)).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(called).isFalse();
    }

    @Test
    void validToken_passesAndPublishesUserId() {
        MockServerWebExchange exchange =
                MockServerWebExchange.from(
                        MockServerHttpRequest.get("/api/orders")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(inOneHour(), KEY)));
        AtomicBoolean called = new AtomicBoolean(false);

        filter.filter(exchange, recordingChain(called)).block();

        assertThat(called).isTrue();
        assertThat(exchange.getAttributes())
                .containsEntry(JwtAuthenticationGlobalFilter.USER_ID_ATTRIBUTE, 7L);
    }

    @Test
    void authEndpoints_passWithoutToken() {
        for (String path :
                new String[] {
                    "/api/auth/register", "/api/auth/login", "/api/auth/refresh",
                }) {
            MockServerWebExchange exchange =
                    MockServerWebExchange.from(MockServerHttpRequest.post(path));
            AtomicBoolean called = new AtomicBoolean(false);

            filter.filter(exchange, recordingChain(called)).block();

            assertThat(called).as("path %s must stay public", path).isTrue();
        }
    }

    @Test
    void observabilityAndDocsPaths_passWithoutToken() {
        for (String path :
                new String[] {
                    "/actuator/health",
                    "/actuator/prometheus",
                    "/swagger-ui.html",
                    "/webjars/swagger-ui/index.html",
                    "/user-service/v3/api-docs",
                    "/product-service/v3/api-docs",
                    "/order-service/v3/api-docs"
                }) {
            MockServerWebExchange exchange =
                    MockServerWebExchange.from(MockServerHttpRequest.get(path));
            AtomicBoolean called = new AtomicBoolean(false);

            filter.filter(exchange, recordingChain(called)).block();

            assertThat(called).as("path %s must stay public", path).isTrue();
        }
    }
}
