package com.walden.cvect.service.matching;

import com.walden.cvect.config.CacheConfig;
import com.walden.cvect.config.CacheProperties;
import com.walden.cvect.infra.embedding.EmbeddingService;
import com.walden.cvect.infra.vector.VectorStoreService;
import com.walden.cvect.logging.aop.TimedActionAspect;
import com.walden.cvect.logging.config.LogProperties;
import com.walden.cvect.model.ChunkType;
import com.walden.cvect.model.TenantConstants;
import com.walden.cvect.repository.CandidateJpaRepository;
import com.walden.cvect.repository.VectorIngestTaskJpaRepository;
import com.walden.cvect.security.CurrentUserService;
import com.walden.cvect.service.matching.SearchQueryEmbeddingCacheService;
import com.walden.cvect.service.matching.SemanticSearchExecutionService;
import com.walden.cvect.service.matching.SemanticSearchService;
import com.walden.cvect.web.controller.search.SearchController;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SemanticSearchCacheTest.TestConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("Semantic search cache tests")
class SemanticSearchCacheTest {

    @Autowired
    private SemanticSearchService semanticSearchService;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private VectorStoreService vectorStoreService;

    @Autowired
    private CandidateJpaRepository candidateRepository;

    @Autowired
    private CurrentUserService currentUserService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private SearchController searchController;

    @Autowired
    private SearchQueryEmbeddingCacheService searchQueryEmbeddingCacheService;

    @BeforeEach
    void setUp() {
        reset(embeddingService, vectorStoreService, candidateRepository, currentUserService);
        cacheManager.getCacheNames().forEach(name -> {
            if (cacheManager.getCache(name) != null) {
                cacheManager.getCache(name).clear();
            }
        });
        float[] embedding = new float[1024];
        when(currentUserService.currentTenantId()).thenReturn(TenantConstants.DEFAULT_TENANT_ID);
        when(candidateRepository.findIdsByTenantId(TenantConstants.DEFAULT_TENANT_ID)).thenReturn(List.of(UUID.randomUUID()));
        when(embeddingService.embed("same jd")).thenReturn(embedding);
        when(vectorStoreService.search(any(float[].class), eq(40), anyCollection(), any(ChunkType[].class))).thenReturn(List.of(
                new VectorStoreService.SearchResult(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        ChunkType.EXPERIENCE,
                        "exp",
                        0.8f)));
    }

    @Test
    @DisplayName("same search request should hit cached response")
    void shouldCacheSearchResponse() {
        double initialRequestCount = timerCount("cvect.search.request");
        double initialComputeCount = timerCount("cvect.search.compute");
        SearchController.SearchRequest request = new SearchController.SearchRequest(
                "same jd",
                10,
                true,
                true,
                0.5f,
                0.5f,
                false);

        searchController.search(request);
        searchController.search(request);

        verify(embeddingService, times(1)).embed("same jd");
        verify(vectorStoreService, times(1)).search(any(float[].class), eq(40), anyCollection(), any(ChunkType[].class));
        assertEquals(initialRequestCount + 2.0d, timerCount("cvect.search.request"), 0.0001d);
        assertEquals(initialComputeCount + 1.0d, timerCount("cvect.search.compute"), 0.0001d);
        assertEquals(0.5d, meterRegistry.get("cvect.cache.hit.rate")
                .tag("cache", CacheConfig.SEARCH_RESPONSE_CACHE)
                .gauge()
                .value(), 0.0001d);
    }

    @Test
    @DisplayName("same JD with different weights should reuse cached query embedding")
    void shouldReuseCachedQueryEmbeddingAcrossDifferentSearchKeys() {
        SearchController.SearchRequest first = new SearchController.SearchRequest(
                "same jd",
                10,
                true,
                true,
                0.7f,
                0.3f,
                false);
        SearchController.SearchRequest second = new SearchController.SearchRequest(
                "same jd",
                10,
                true,
                true,
                0.4f,
                0.6f,
                false);

        searchController.search(first);
        searchController.search(second);

        verify(embeddingService, times(1)).embed("same jd");
        verify(vectorStoreService, times(2)).search(any(float[].class), eq(40), anyCollection(), any(ChunkType[].class));
        assertEquals(0.5d, meterRegistry.get("cvect.cache.hit.rate")
                .tag("cache", CacheConfig.SEARCH_QUERY_EMBEDDING_CACHE)
                .gauge()
                .value(), 0.0001d);
    }

    @Test
    @DisplayName("whitespace-equivalent JD text should embed normalized text once")
    void shouldEmbedNormalizedWhitespaceEquivalentText() {
        searchQueryEmbeddingCacheService.get("  same \n   jd  ");
        searchQueryEmbeddingCacheService.get("same jd");

        verify(embeddingService, times(1)).embed("same jd");
    }

    @Test
    @DisplayName("equivalent normalized weights should reuse cached search response")
    void shouldReuseCachedSearchResponseAcrossEquivalentWeights() {
        SearchController.SearchRequest first = new SearchController.SearchRequest(
                "same jd",
                10,
                true,
                true,
                0.2f,
                0.8f,
                false);
        SearchController.SearchRequest second = new SearchController.SearchRequest(
                "same jd",
                10,
                true,
                true,
                0.4f,
                1.6f,
                false);

        searchController.search(first);
        searchController.search(second);

        verify(embeddingService, times(1)).embed("same jd");
        verify(vectorStoreService, times(1)).search(any(float[].class), eq(40), anyCollection(), any(ChunkType[].class));
        assertEquals(0.5d, meterRegistry.get("cvect.cache.hit.rate")
                .tag("cache", CacheConfig.SEARCH_RESPONSE_CACHE)
                .gauge()
                .value(), 0.0001d);
    }

    private double timerCount(String name) {
        var timer = meterRegistry.find(name).tag("outcome", "success").timer();
        return timer == null ? 0.0d : timer.count();
    }

    @Configuration
    @EnableCaching
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class TestConfig {
        @Bean
        CacheProperties cacheProperties() {
            CacheProperties properties = new CacheProperties();
            properties.getSearch().getQueryEmbedding().setTtl(Duration.ofMinutes(10));
            properties.getSearch().getQueryEmbedding().setMaximumSize(16);
            properties.getSearch().getResponse().setTtl(Duration.ofMinutes(1));
            properties.getSearch().getResponse().setMaximumSize(16);
            return properties;
        }

        @Bean
        org.springframework.cache.CacheManager cacheManager(
                CacheProperties cacheProperties,
                ObjectProvider<MeterRegistry> meterRegistryProvider) {
            return new CacheConfig().cacheManager(cacheProperties, meterRegistryProvider);
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        LogProperties logProperties() {
            return new LogProperties();
        }

        @Bean
        TimedActionAspect timedActionAspect(LogProperties logProperties, ObjectProvider<MeterRegistry> meterRegistryProvider) {
            return new TimedActionAspect(logProperties, meterRegistryProvider);
        }

        @Bean
        EmbeddingService embeddingService() {
            return mock(EmbeddingService.class);
        }

        @Bean
        VectorStoreService vectorStoreService() {
            return mock(VectorStoreService.class);
        }

        @Bean
        VectorIngestTaskJpaRepository vectorIngestTaskJpaRepository() {
            return mock(VectorIngestTaskJpaRepository.class);
        }

        @Bean
        CandidateJpaRepository candidateJpaRepository() {
            return mock(CandidateJpaRepository.class);
        }

        @Bean
        CurrentUserService currentUserService() {
            return mock(CurrentUserService.class);
        }

        @Bean
        SearchQueryEmbeddingCacheService searchQueryEmbeddingCacheService(EmbeddingService embeddingService) {
            return new SearchQueryEmbeddingCacheService(embeddingService);
        }

        @Bean
        SemanticSearchExecutionService semanticSearchExecutionService(
                VectorStoreService vectorStoreService,
                SearchQueryEmbeddingCacheService searchQueryEmbeddingCacheService,
                VectorIngestTaskJpaRepository vectorIngestTaskJpaRepository,
                CandidateJpaRepository candidateJpaRepository,
                CurrentUserService currentUserService) {
            return new SemanticSearchExecutionService(
                    vectorStoreService,
                    searchQueryEmbeddingCacheService,
                    vectorIngestTaskJpaRepository,
                    candidateJpaRepository,
                    currentUserService);
        }

        @Bean
        SemanticSearchService semanticSearchService(SemanticSearchExecutionService semanticSearchExecutionService) {
            return new SemanticSearchService(semanticSearchExecutionService);
        }

        @Bean
        SearchController searchController(
                VectorStoreService vectorStoreService,
                SemanticSearchService semanticSearchService) {
            return new SearchController(vectorStoreService, semanticSearchService);
        }
    }
}
