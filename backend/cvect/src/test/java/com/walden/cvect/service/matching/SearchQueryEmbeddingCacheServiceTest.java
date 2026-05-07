package com.walden.cvect.service.matching;

import com.walden.cvect.config.CacheConfig;
import com.walden.cvect.infra.embedding.EmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SearchQueryEmbeddingCacheServiceTest.TestConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("SearchQueryEmbeddingCacheService cache tests")
class SearchQueryEmbeddingCacheServiceTest {

    @Autowired
    private SearchQueryEmbeddingCacheService service;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        reset(embeddingService);
        cacheManager.getCacheNames().forEach(cacheName -> {
            if (cacheManager.getCache(cacheName) != null) {
                cacheManager.getCache(cacheName).clear();
            }
        });
    }

    @Test
    @DisplayName("shouldReuseCachedEmbeddingForSameQuery")
    void shouldReuseCachedEmbeddingForSameQuery() {
        float[] embedding = new float[] {0.1f, 0.2f};
        when(embeddingService.embed("Java backend role")).thenReturn(embedding);

        float[] first = service.get("Java backend role");
        float[] second = service.get("Java backend role");

        assertThat(first).isSameAs(second);
        verify(embeddingService, times(1)).embed("Java backend role");
    }

    @Test
    @DisplayName("shouldCallEmbeddingServiceSeparatelyForDifferentQueries")
    void shouldCallEmbeddingServiceSeparatelyForDifferentQueries() {
        when(embeddingService.embed("Java backend role")).thenReturn(new float[] {0.1f});
        when(embeddingService.embed("Python backend role")).thenReturn(new float[] {0.2f});

        service.get("Java backend role");
        service.get("Python backend role");

        verify(embeddingService, times(1)).embed("Java backend role");
        verify(embeddingService, times(1)).embed("Python backend role");
    }

    @Test
    @DisplayName("shouldNotCacheInvalidBlankQuery")
    void shouldNotCacheInvalidBlankQuery() {
        when(embeddingService.embed("")).thenThrow(new IllegalArgumentException("jobDescription must not be blank"));

        assertThatThrownBy(() -> service.get("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
        assertThatThrownBy(() -> service.get(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");

        verify(embeddingService, times(2)).embed("");
    }

    @Configuration
    @EnableCaching
    static class TestConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(CacheConfig.SEARCH_QUERY_EMBEDDING_CACHE);
        }

        @Bean
        EmbeddingService embeddingService() {
            return mock(EmbeddingService.class);
        }

        @Bean
        SearchQueryEmbeddingCacheService searchQueryEmbeddingCacheService(EmbeddingService embeddingService) {
            return new SearchQueryEmbeddingCacheService(embeddingService);
        }
    }
}
