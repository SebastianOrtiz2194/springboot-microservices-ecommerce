package com.ecommerce.product.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.Callable;
import org.springframework.cache.Cache;

/**
 * Decorates a {@link Cache} and counts hits/misses on the {@link #get(Object)} path used by
 * {@code @Cacheable} (sync=false). All other operations are delegated unchanged.
 */
public class MeteredCache implements Cache {

    private final Cache delegate;
    private final Counter hits;
    private final Counter misses;

    public MeteredCache(Cache delegate, MeterRegistry meterRegistry) {
        this.delegate = delegate;
        this.hits =
                Counter.builder("cache_hit_total")
                        .description("Cache lookups served from cache")
                        .tag("cache", delegate.getName())
                        .register(meterRegistry);
        this.misses =
                Counter.builder("cache_miss_total")
                        .description("Cache lookups that missed and hit the backing store")
                        .tag("cache", delegate.getName())
                        .register(meterRegistry);
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public Object getNativeCache() {
        return delegate.getNativeCache();
    }

    @Override
    public ValueWrapper get(Object key) {
        ValueWrapper wrapper = delegate.get(key);
        if (wrapper != null) {
            hits.increment();
        } else {
            misses.increment();
        }
        return wrapper;
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
        return delegate.get(key, type);
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        return delegate.get(key, valueLoader);
    }

    @Override
    public void put(Object key, Object value) {
        delegate.put(key, value);
    }

    @Override
    public void evict(Object key) {
        delegate.evict(key);
    }

    @Override
    public void clear() {
        delegate.clear();
    }
}
