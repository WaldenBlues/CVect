package com.walden.cvect.service.matching;

import com.walden.cvect.config.CacheConfig;
import com.walden.cvect.web.controller.search.SearchController;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SemanticSearchService {
    private final SemanticSearchExecutionService executionService;

    public SemanticSearchService(SemanticSearchExecutionService executionService) {
        this.executionService = executionService;
    }

    @Cacheable(
            cacheNames = CacheConfig.SEARCH_RESPONSE_CACHE,
            key = "T(com.walden.cvect.service.matching.SearchCacheKeys).searchRequest(#request)",
            sync = true)
    public SearchController.SearchResponse search(SearchController.SearchRequest request) {
        return executionService.search(request);
    }
}
