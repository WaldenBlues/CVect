package com.walden.cvect.service;

import com.walden.cvect.config.CacheConfig;
import com.walden.cvect.infra.embedding.EmbeddingService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class SearchQueryEmbeddingCacheService {

    private final EmbeddingService embeddingService;

    public SearchQueryEmbeddingCacheService(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @Cacheable(
            cacheNames = CacheConfig.SEARCH_QUERY_EMBEDDING_CACHE,
            key = "T(com.walden.cvect.service.SearchCacheKeys).queryEmbedding(#jobDescription)",
            sync = true)
    public float[] get(String jobDescription) {
        return embeddingService.embed(SearchCacheKeys.normalizeText(jobDescription));
    }
}
