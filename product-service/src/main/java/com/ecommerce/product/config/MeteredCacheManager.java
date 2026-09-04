package com.ecommerce.product.config;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collection;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/**
 * Decorates a {@link CacheManager} so every returned {@link Cache} reports hit/miss metrics to
 * Micrometer. Enables {@code cache_hit_total} / {@code cache_miss_total} counters for caches (like
 * Redis) that do not expose local statistics.
 */
public class MeteredCacheManager implements CacheManager {

    private final CacheManager delegate;
    private final MeterRegistry meterRegistry;

    public MeteredCacheManager(CacheManager delegate, MeterRegistry meterRegistry) {
        this.delegate = delegate;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Cache getCache(String name) {
        Cache cache = delegate.getCache(name);
        return cache == null ? null : new MeteredCache(cache, meterRegistry);
    }

    @Override
    public Collection<String> getCacheNames() {
        return delegate.getCacheNames();
    }
}
