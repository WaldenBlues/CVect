package com.walden.cvect.web.controller;

import com.walden.cvect.infra.vector.VectorStoreService;
import com.walden.cvect.service.SemanticSearchService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SearchController unit tests")
class SearchControllerTest {

    @Mock
    private VectorStoreService vectorStore;

    @Mock
    private SemanticSearchService semanticSearchService;

    private SearchController controller;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        controller = new SearchController(
                vectorStore,
                semanticSearchService,
                new FixedObjectProvider<>(meterRegistry));
    }

    @Test
    @DisplayName("search should delegate to semantic search service")
    void shouldDelegateSearchToService() {
        SearchController.SearchRequest request = new SearchController.SearchRequest(
                "Java backend role",
                10,
                true,
                true,
                0.6f,
                0.4f,
                false);
        SearchController.SearchResponse expected = new SearchController.SearchResponse(
                1,
                10,
                List.of(new SearchController.CandidateMatch(
                        UUID.randomUUID(),
                        List.of(),
                        0.81f)));
        when(semanticSearchService.search(request)).thenReturn(expected);

        ResponseEntity<SearchController.SearchResponse> response = controller.search(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(expected, response.getBody());
        verify(semanticSearchService).search(request);
        assertEquals(1L, meterRegistry.get("cvect.search.request").tag("outcome", "success").timer().count());
    }

    private static final class FixedObjectProvider<T> implements ObjectProvider<T> {
        private final T value;

        private FixedObjectProvider(T value) {
            this.value = value;
        }

        @Override
        public T getObject(Object... args) {
            return value;
        }

        @Override
        public T getIfAvailable() {
            return value;
        }

        @Override
        public T getIfUnique() {
            return value;
        }

        @Override
        public T getObject() {
            return value;
        }
    }
}
