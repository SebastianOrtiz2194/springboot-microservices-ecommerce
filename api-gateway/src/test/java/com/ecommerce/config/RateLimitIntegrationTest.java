package com.ecommerce.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies the gateway's {@code RequestRateLimiter} wiring against a real Redis: bursts beyond the
 * configured capacity are answered with {@code 429 Too Many Requests} without reaching any
 * downstream service. The bucket is intentionally tiny via test properties so the behaviour is
 * deterministic and fast.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "RATE_LIMIT_REPLENISH=1",
            "RATE_LIMIT_BURST=2",
            "AUTH_RATE_LIMIT_REPLENISH=1",
            "AUTH_RATE_LIMIT_BURST=2",
            "eureka.client.enabled=false"
        })
@Testcontainers
class RateLimitIntegrationTest {

    private static final String SECRET =
            "dev-secret-key-that-is-at-least-32-chars-long-for-local-dev-only-please-change";

    // Boot 3.3 ships no @ServiceConnection factory for Redis — wire host/port explicitly
    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired private TestRestTemplate restTemplate;

    private static String validToken() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject("alice@example.com")
                .claim("userId", 7L)
                .claim("role", "USER")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(key)
                .compact();
    }

    @Test
    void burstBeyondCapacity_returnsTooManyRequests() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + validToken());

        List<HttpStatusCode> statuses =
                burst(
                        8,
                        () ->
                                restTemplate
                                        .exchange(
                                                "/api/products",
                                                HttpMethod.GET,
                                                new HttpEntity<>(headers),
                                                String.class)
                                        .getStatusCode());

        assertThat(statuses)
                .as("requests beyond burstCapacity must be throttled: %s", statuses)
                .contains(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void publicAuthEndpoint_isRateLimitedPerIpWithoutToken() throws Exception {
        List<HttpStatusCode> statuses =
                burst(
                        8,
                        () ->
                                restTemplate
                                        .exchange(
                                                "/api/auth/login",
                                                HttpMethod.POST,
                                                new HttpEntity<>("{}", jsonHeaders()),
                                                String.class)
                                        .getStatusCode());

        assertThat(statuses)
                .as("brute-force bursts on login must be throttled: %s", statuses)
                .contains(HttpStatus.TOO_MANY_REQUESTS);
    }

    /**
     * Fires all requests concurrently: the limiter refills 1 token/second, so only a true burst can
     * exhaust the bucket deterministically. Each call is bounded so the test cannot hang.
     */
    private static List<HttpStatusCode> burst(int count, Supplier<HttpStatusCode> call)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        try {
            List<Future<HttpStatusCode>> futures =
                    IntStream.range(0, count).mapToObj(i -> pool.submit(call::get)).toList();
            List<HttpStatusCode> statuses = new ArrayList<>(count);
            for (Future<HttpStatusCode> future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    private static HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, "application/json");
        return headers;
    }
}
