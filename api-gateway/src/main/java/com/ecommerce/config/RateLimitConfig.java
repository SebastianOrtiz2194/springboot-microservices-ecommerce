package com.ecommerce.config;

import java.net.InetSocketAddress;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Rate-limit key strategies for the gateway's {@code RequestRateLimiter} filter. Public endpoints
 * are keyed per client IP (slows brute force on login/register), authenticated endpoints per user
 * so one noisy account cannot exhaust the bucket of others.
 */
@Configuration
public class RateLimitConfig {

    /**
     * Keys requests per authenticated user id (as validated by {@link
     * JwtAuthenticationGlobalFilter}), falling back to the client IP when no token was presented.
     * Primary so it serves as the gateway's default rate-limit key; routes select {@link
     * #ipKeyResolver()} explicitly via SpEL.
     *
     * @return the per-user key resolver
     */
    @Bean
    @Primary
    public KeyResolver userKeyResolver() {
        return exchange -> {
            Object userId =
                    exchange.getAttributes().get(JwtAuthenticationGlobalFilter.USER_ID_ATTRIBUTE);
            if (userId != null) {
                return Mono.just("user:" + userId);
            }
            return Mono.just("ip:" + clientIp(exchange));
        };
    }

    /**
     * Keys requests per client IP regardless of authentication.
     *
     * @return the per-IP key resolver
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just("ip:" + clientIp(exchange));
    }

    private static String clientIp(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return remote != null && remote.getAddress() != null
                ? remote.getAddress().getHostAddress()
                : "unknown";
    }
}
