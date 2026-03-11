package com.walden.cvect.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cache.annotation.EnableCaching;

import java.util.List;

@Configuration
@EnableCaching
@EnableConfigurationProperties(CacheProperties.class)
public class CacheConfig {
    public static final String SEARCH_QUERY_EMBEDDING_CACHE = "searchQueryEmbeddings";
    public static final String SEARCH_RESPONSE_CACHE = "semanticSearchResponses";

    @Bean
    public CacheManager cacheManager(
            CacheProperties properties,
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        if (!properties.isEnabled()) {
            return new NoOpCacheManager();
        }

        CaffeineCache queryEmbeddingCache = buildCache(
                SEARCH_QUERY_EMBEDDING_CACHE,
                properties.getSearch().getQueryEmbedding());
        CaffeineCache searchResponseCache = buildCache(
                SEARCH_RESPONSE_CACHE,
                properties.getSearch().getResponse());
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(List.of(queryEmbeddingCache, searchResponseCache));
        MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
        if (meterRegistry != null) {
            bindMetrics(queryEmbeddingCache, meterRegistry);
            bindMetrics(searchResponseCache, meterRegistry);
        }
        return cacheManager;
    }

    private CaffeineCache buildCache(String name, CacheProperties.CacheSpec spec) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .recordStats()
                .expireAfterWrite(spec.getTtl())
                .maximumSize(Math.max(1L, spec.getMaximumSize()))
                .build());
    }

    private void bindMetrics(CaffeineCache cache, MeterRegistry meterRegistry) {
        com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache = cache.getNativeCache();
        CaffeineCacheMetrics.monitor(meterRegistry, nativeCache, cache.getName(), Tags.of("app", "cvect"));
        Gauge.builder("cvect.cache.hit.rate", nativeCache, currentCache -> currentCache.stats().hitRate())
                .tag("cache", cache.getName())
                .tag("app", "cvect")
                .description("Current hit rate for CVect caches")
                .register(meterRegistry);
    }
}
