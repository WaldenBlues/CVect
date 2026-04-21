package com.walden.cvect.web.controller.search;

import com.walden.cvect.infra.vector.VectorStoreService;
import com.walden.cvect.service.matching.SemanticSearchService;
import com.walden.cvect.web.controller.search.SearchController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

    @BeforeEach
    void setUp() {
        controller = new SearchController(
                vectorStore,
                semanticSearchService);
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
    }

    @Test
    @DisplayName("admin create-index should report the configured index type")
    void shouldReportConfiguredIndexTypeWhenCreatingIndex() {
        when(vectorStore.getResolvedIndexType()).thenReturn("ivfflat");

        ResponseEntity<String> response = controller.createIndex();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("IVFFLAT index created successfully", response.getBody());
        verify(vectorStore).createVectorIndex();
    }
}
