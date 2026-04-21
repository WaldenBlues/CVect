package com.walden.cvect.service.matching;

import com.walden.cvect.web.controller.search.SearchController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("SearchWeightNormalizer unit tests")
class SearchWeightNormalizerTest {

    @Test
    @DisplayName("resolve should default to balanced weights when both filters are enabled and weights are absent")
    void shouldDefaultToBalancedWeightsWhenWeightsAreAbsent() {
        SearchController.SearchRequest request = new SearchController.SearchRequest(
                "Java backend role",
                10,
                true,
                true,
                null,
                null,
                false);

        SearchWeightNormalizer.Weights weights = SearchWeightNormalizer.resolve(request);

        assertEquals(0.5f, weights.experienceWeight(), 0.0001f);
        assertEquals(0.5f, weights.skillWeight(), 0.0001f);
    }

    @Test
    @DisplayName("resolve should fall back to balanced weights when no filters are selected")
    void shouldFallbackToBalancedWeightsWhenNoFiltersAreSelected() {
        SearchController.SearchRequest request = new SearchController.SearchRequest(
                "Java backend role",
                10,
                false,
                false,
                0.9f,
                0.1f,
                false);

        SearchWeightNormalizer.Weights weights = SearchWeightNormalizer.resolve(request);

        assertEquals(0.5f, weights.experienceWeight(), 0.0001f);
        assertEquals(0.5f, weights.skillWeight(), 0.0001f);
    }

    @Test
    @DisplayName("resolve should ignore invalid custom weights and normalize the remaining values")
    void shouldIgnoreInvalidCustomWeights() {
        SearchController.SearchRequest request = new SearchController.SearchRequest(
                "Java backend role",
                10,
                true,
                true,
                Float.NaN,
                3.0f,
                false);

        SearchWeightNormalizer.Weights weights = SearchWeightNormalizer.resolve(request);

        assertEquals(0.14285715f, weights.experienceWeight(), 0.0001f);
        assertEquals(0.85714287f, weights.skillWeight(), 0.0001f);
    }

    @Test
    @DisplayName("resolve should force a single enabled filter to weight 1")
    void shouldForceSingleEnabledFilterToOne() {
        SearchController.SearchRequest request = new SearchController.SearchRequest(
                "Java backend role",
                10,
                true,
                false,
                0.2f,
                0.8f,
                false);

        SearchWeightNormalizer.Weights weights = SearchWeightNormalizer.resolve(request);

        assertEquals(1.0f, weights.experienceWeight(), 0.0001f);
        assertEquals(0.0f, weights.skillWeight(), 0.0001f);
    }
}
