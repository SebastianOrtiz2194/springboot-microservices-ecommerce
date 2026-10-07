package com.ecommerce.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

/** Unit tests for the rate-limit key strategies in {@link RateLimitConfig}. */
class RateLimitConfigTest {

    private final RateLimitConfig config = new RateLimitConfig();

    private static MockServerWebExchange exchangeFrom(String ip) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/products")
                        .remoteAddress(new InetSocketAddress(ip, 51234)));
    }

    @Test
    void userKeyResolver_prefersValidatedUserId() {
        MockServerWebExchange exchange = exchangeFrom("10.0.0.5");
        exchange.getAttributes().put(JwtAuthenticationGlobalFilter.USER_ID_ATTRIBUTE, 7L);

        assertThat(config.userKeyResolver().resolve(exchange).block()).isEqualTo("user:7");
    }

    @Test
    void userKeyResolver_fallsBackToClientIp() {
        MockServerWebExchange exchange = exchangeFrom("10.0.0.5");

        assertThat(config.userKeyResolver().resolve(exchange).block()).isEqualTo("ip:10.0.0.5");
    }

    @Test
    void ipKeyResolver_keysByClientIpRegardlessOfUser() {
        MockServerWebExchange exchange = exchangeFrom("10.0.0.5");
        exchange.getAttributes().put(JwtAuthenticationGlobalFilter.USER_ID_ATTRIBUTE, 7L);

        assertThat(config.ipKeyResolver().resolve(exchange).block()).isEqualTo("ip:10.0.0.5");
    }

    @Test
    void resolvers_tolerateMissingRemoteAddress() {
        MockServerWebExchange exchange =
                MockServerWebExchange.from(MockServerHttpRequest.get("/api/auth/login"));

        assertThat(config.ipKeyResolver().resolve(exchange).block()).isEqualTo("ip:unknown");
    }
}
