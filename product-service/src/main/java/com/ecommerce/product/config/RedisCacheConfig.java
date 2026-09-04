package com.ecommerce.product.config;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

/**
 * Configures Redis caching with JSON serialization, TTL, and Micrometer hit/miss metrics. Prevents
 * JDK serialization issues and infinite TTL.
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {

    @Bean
    public CacheManager cacheManager(
            RedisConnectionFactory connectionFactory, MeterRegistry meterRegistry) {
        RedisCacheConfiguration config =
                RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofMinutes(10))
                        .disableCachingNullValues()
                        .serializeValuesWith(
                                RedisSerializationContext.SerializationPair.fromSerializer(
                                        new GenericJackson2JsonRedisSerializer()));

        RedisCacheManager delegate =
                RedisCacheManager.builder(connectionFactory)
                        .cacheDefaults(config)
                        .transactionAware()
                        .build();

        return new MeteredCacheManager(delegate, meterRegistry);
    }
}
