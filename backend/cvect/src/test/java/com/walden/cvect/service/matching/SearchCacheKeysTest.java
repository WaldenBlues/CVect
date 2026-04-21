package com.walden.cvect.service.matching;

import com.walden.cvect.web.controller.search.SearchController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("SearchCacheKeys unit tests")
class SearchCacheKeysTest {

    @Test
    @DisplayName("searchRequest should ignore custom weights when only one filter is enabled")
    void shouldIgnoreCustomWeightsForSingleEnabledFilter() {
        SearchController.SearchRequest canonicalRequest = new SearchController.SearchRequest(
                "Java backend role",
                10,
                true,
                false,
                0.2f,
                0.8f,
                false);
        SearchController.SearchRequest alternativeWeights = new SearchController.SearchRequest(
                "Java backend role",
                10,
                true,
                false,
                0.9f,
                3.0f,
                false);

        String canonicalKey = SearchCacheKeys.searchRequest(canonicalRequest);
        String alternativeKey = SearchCacheKeys.searchRequest(alternativeWeights);

        assertEquals(canonicalKey, alternativeKey);
    }
}
