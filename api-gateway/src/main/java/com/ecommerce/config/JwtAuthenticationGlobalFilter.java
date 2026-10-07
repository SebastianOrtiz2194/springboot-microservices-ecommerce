package com.ecommerce.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Validates JWT bearer tokens at the edge, before a request is routed to a service. Public paths
 * (auth, actuator, OpenAPI docs) are skipped; everything else must present a token signed with the
 * platform key and must not be expired. Failures are answered here with an RFC 7807 body and 401,
 * so invalid traffic never reaches a downstream service.
 *
 * <p>The same {@code app.jwt.secret} and claims ({@code userId}, {@code role}, subject email) as
 * the services are used; each service still re-validates the token itself (defence in depth). On
 * success the validated user id is published as an exchange attribute so the rate limiter can key
 * requests per user.
 */
@Component
public class JwtAuthenticationGlobalFilter implements GlobalFilter, Ordered {

    /** Exchange attribute holding the validated user id, for per-user rate limiting. */
    public static final String USER_ID_ATTRIBUTE = "jwt.userId";

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationGlobalFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";

    private final SecretKey signingKey;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationGlobalFilter(
            @Value("${app.jwt.secret}") String secret, ObjectMapper objectMapper) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT secret is not configured. Set JWT_SECRET env var (min 32 chars / 256 bits)");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "JWT secret must be at least 32 bytes (256 bits) for HS256. Current length: "
                            + secret.getBytes(StandardCharsets.UTF_8).length);
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (isPublic(path)) {
            return chain.filter(exchange);
        }

        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange, "Missing bearer token");
        }

        try {
            Claims claims =
                    Jwts.parser()
                            .verifyWith(signingKey)
                            .build()
                            .parseSignedClaims(header.substring(BEARER_PREFIX.length()))
                            .getPayload();
            exchange.getAttributes().put(USER_ID_ATTRIBUTE, claims.get("userId", Long.class));
            return chain.filter(exchange);
        } catch (Exception ex) {
            log.warn("gateway_jwt_rejected path={} error={}", path, ex.getMessage());
            return unauthorized(exchange, "Invalid or expired token");
        }
    }

    @Override
    public int getOrder() {
        // Before route filters (rate limiting, routing): reject bad credentials as cheaply as
        // possible.
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private static boolean isPublic(String path) {
        return path.startsWith("/api/auth/")
                || path.startsWith("/actuator/")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/webjars/")
                || path.startsWith("/favicon.ico")
                || path.startsWith("/error")
                || path.contains("/v3/api-docs");
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String detail) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", "Unauthorized");
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        body.put("detail", detail);
        body.put("instance", exchange.getRequest().getPath().value());
        DataBuffer buffer;
        try {
            buffer = response.bufferFactory().wrap(objectMapper.writeValueAsBytes(body));
        } catch (Exception ex) {
            buffer =
                    response.bufferFactory()
                            .wrap(
                                    "{\"title\":\"Unauthorized\",\"status\":401}"
                                            .getBytes(StandardCharsets.UTF_8));
        }
        ServerHttpRequest request = exchange.getRequest();
        log.warn(
                "gateway_jwt_unauthorized path={} method={}",
                request.getPath().value(),
                request.getMethod());
        return response.writeWith(Mono.just(buffer));
    }
}
