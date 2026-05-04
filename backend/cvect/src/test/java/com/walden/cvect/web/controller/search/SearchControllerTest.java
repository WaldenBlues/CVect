package com.walden.cvect.web.controller.search;

import com.walden.cvect.infra.vector.VectorStoreService;
import com.walden.cvect.service.matching.SemanticSearchService;
import com.walden.cvect.web.controller.search.SearchController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("SearchController unit tests")
class SearchControllerTest {

    @Mock
    private VectorStoreService vectorStore;

    @Mock
    private SemanticSearchService semanticSearchService;

    private SearchController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        controller = new SearchController(
                vectorStore,
                semanticSearchService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
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
    @DisplayName("search should bind custom Experience and Skill weights from request JSON before delegating to service")
    void shouldBindCustomWeightsFromJsonRequest() throws Exception {
        when(semanticSearchService.search(any())).thenReturn(new SearchController.SearchResponse(0, 15, List.of()));

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobDescription": "Java backend role",
                                  "topK": 15,
                                  "filterByExperience": true,
                                  "filterBySkill": true,
                                  "experienceWeight": 0.8,
                                  "skillWeight": 0.2,
                                  "onlyVectorReadyCandidates": true
                                }
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<SearchController.SearchRequest> requestCaptor =
                ArgumentCaptor.forClass(SearchController.SearchRequest.class);
        verify(semanticSearchService).search(requestCaptor.capture());

        SearchController.SearchRequest captured = requestCaptor.getValue();
        assertEquals("Java backend role", captured.jobDescription());
        assertEquals(15, captured.topK());
        assertEquals(true, captured.filterByExperience());
        assertEquals(true, captured.filterBySkill());
        assertEquals(0.8f, captured.experienceWeight(), 0.0001f);
        assertEquals(0.2f, captured.skillWeight(), 0.0001f);
        assertEquals(true, captured.onlyVectorReadyCandidates());
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
