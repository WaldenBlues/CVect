package com.walden.cvect.web.controller;

import com.walden.cvect.infra.embedding.EmbeddingService;
import com.walden.cvect.infra.vector.VectorStoreService;
import com.walden.cvect.model.ChunkType;
import com.walden.cvect.model.entity.vector.VectorIngestTaskStatus;
import com.walden.cvect.repository.VectorIngestTaskJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SearchController unit tests")
class SearchControllerTest {

    @Mock
    private VectorStoreService vectorStore;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private VectorIngestTaskJpaRepository vectorIngestTaskRepository;

    private SearchController controller;

    @BeforeEach
    void setUp() {
        controller = new SearchController(vectorStore, embeddingService, vectorIngestTaskRepository);
    }

    @Test
    @DisplayName("search should aggregate by candidate and sort by weighted score")
    void shouldAggregateAndSortByWeightedScore() {
        UUID candidateA = UUID.randomUUID();
        UUID candidateB = UUID.randomUUID();
        float[] embedding = new float[1024];
        when(embeddingService.embed("Java backend role")).thenReturn(embedding);
        when(vectorStore.search(any(float[].class), eq(40), any(ChunkType[].class))).thenReturn(List.of(
                new VectorStoreService.SearchResult(UUID.randomUUID(), candidateA, ChunkType.EXPERIENCE, "A-exp", 0.9f),
                new VectorStoreService.SearchResult(UUID.randomUUID(), candidateA, ChunkType.SKILL, "A-skill", 0.3f),
                new VectorStoreService.SearchResult(UUID.randomUUID(), candidateB, ChunkType.EXPERIENCE, "B-exp", 0.5f),
                new VectorStoreService.SearchResult(UUID.randomUUID(), candidateB, ChunkType.SKILL, "B-skill", 0.5f)
        ));

        SearchController.SearchRequest request = new SearchController.SearchRequest(
                "Java backend role",
                10,
                true,
                true,
                null,
                null,
                false
        );

        ResponseEntity<SearchController.SearchResponse> response = controller.search(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().totalResults());
        assertEquals(10, response.getBody().requested());
        Map<UUID, Float> scoreByCandidate = response.getBody().candidates().stream()
                .collect(Collectors.toMap(SearchController.CandidateMatch::candidateId, SearchController.CandidateMatch::score));
        assertEquals(0.4f, scoreByCandidate.get(candidateA), 0.0001f);
        assertEquals(0.5f, scoreByCandidate.get(candidateB), 0.0001f);
    }

    @Test
    @DisplayName("single-type search should force that type weight to 1")
    void shouldForceWeightToOneForSingleTypeSearch() {
        UUID candidateA = UUID.randomUUID();
        UUID candidateB = UUID.randomUUID();
        float[] embedding = new float[1024];
        when(embeddingService.embed("experience only")).thenReturn(embedding);
        when(vectorStore.search(any(float[].class), eq(20), any(ChunkType[].class))).thenReturn(List.of(
                new VectorStoreService.SearchResult(UUID.randomUUID(), candidateA, ChunkType.EXPERIENCE, "A-exp", 0.2f),
                new VectorStoreService.SearchResult(UUID.randomUUID(), candidateB, ChunkType.EXPERIENCE, "B-exp", 0.9f)
        ));

        SearchController.SearchRequest request = new SearchController.SearchRequest(
                "experience only",
                5,
                true,
                false,
                0.1f,
                0.9f,
                false
        );

        ResponseEntity<SearchController.SearchResponse> response = controller.search(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        Map<UUID, Float> scoreByCandidate = response.getBody().candidates().stream()
                .collect(Collectors.toMap(SearchController.CandidateMatch::candidateId, SearchController.CandidateMatch::score));
        assertEquals(0.8f, scoreByCandidate.get(candidateA), 0.0001f);
        assertEquals(0.1f, scoreByCandidate.get(candidateB), 0.0001f);
    }

    @Test
    @DisplayName("search should fall back to max overall score when weighted score is zero")
    void shouldFallbackToOverallScoreWhenNoWeightedMatch() {
        UUID candidateA = UUID.randomUUID();
        UUID candidateB = UUID.randomUUID();
        float[] embedding = new float[1024];
        when(embeddingService.embed("all types")).thenReturn(embedding);
        when(vectorStore.search(any(float[].class), eq(40), org.mockito.ArgumentMatchers.<ChunkType[]>isNull())).thenReturn(List.of(
                new VectorStoreService.SearchResult(UUID.randomUUID(), candidateA, ChunkType.OTHER, "A-other", 0.2f),
                new VectorStoreService.SearchResult(UUID.randomUUID(), candidateB, ChunkType.OTHER, "B-other", 0.8f)
        ));

        SearchController.SearchRequest request = new SearchController.SearchRequest(
                "all types",
                10,
                false,
                false,
                null,
                null,
                false
        );

        ResponseEntity<SearchController.SearchResponse> response = controller.search(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        Map<UUID, Float> scoreByCandidate = response.getBody().candidates().stream()
                .collect(Collectors.toMap(SearchController.CandidateMatch::candidateId, SearchController.CandidateMatch::score));
        assertEquals(0.8f, scoreByCandidate.get(candidateA), 0.0001f);
        assertEquals(0.2f, scoreByCandidate.get(candidateB), 0.0001f);
    }

    @Test
    @DisplayName("search should filter only vector-ready candidates when requested")
    void shouldFilterOnlyVectorReadyCandidates() {
        UUID pendingCandidate = UUID.randomUUID();
        UUID readyCandidate = UUID.randomUUID();
        UUID notReadyCandidate = UUID.randomUUID();
        float[] embedding = new float[1024];
        when(embeddingService.embed("ready only")).thenReturn(embedding);
        when(vectorStore.search(any(float[].class), eq(40), any(ChunkType[].class))).thenReturn(List.of(
                new VectorStoreService.SearchResult(UUID.randomUUID(), pendingCandidate, ChunkType.EXPERIENCE, "pending", 0.9f),
                new VectorStoreService.SearchResult(UUID.randomUUID(), readyCandidate, ChunkType.EXPERIENCE, "ready", 0.8f),
                new VectorStoreService.SearchResult(UUID.randomUUID(), notReadyCandidate, ChunkType.EXPERIENCE, "not-ready", 0.7f)
        ));

        when(vectorIngestTaskRepository.findCandidateIdsByStatusIn(
                any(),
                eq(List.of(VectorIngestTaskStatus.PENDING, VectorIngestTaskStatus.PROCESSING))))
                .thenReturn(List.of(pendingCandidate));
        when(vectorIngestTaskRepository.findCandidateIdsByStatusIn(
                any(),
                eq(List.of(VectorIngestTaskStatus.DONE))))
                .thenReturn(List.of(readyCandidate));

        SearchController.SearchRequest request = new SearchController.SearchRequest(
                "ready only",
                10,
                true,
                true,
                null,
                null,
                true
        );

        ResponseEntity<SearchController.SearchResponse> response = controller.search(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().totalResults());
        assertEquals(readyCandidate, response.getBody().candidates().get(0).candidateId());
    }

    @Test
    @DisplayName("search should not call vector-ready repository checks when filtering disabled")
    void shouldSkipVectorReadyChecksWhenNotRequested() {
        UUID candidate = UUID.randomUUID();
        float[] embedding = new float[1024];
        when(embeddingService.embed("normal search")).thenReturn(embedding);
        when(vectorStore.search(any(float[].class), eq(40), any(ChunkType[].class))).thenReturn(List.of(
                new VectorStoreService.SearchResult(UUID.randomUUID(), candidate, ChunkType.EXPERIENCE, "normal", 0.7f)
        ));

        SearchController.SearchRequest request = new SearchController.SearchRequest(
                "normal search",
                10,
                true,
                true,
                null,
                null,
                false
        );

        ResponseEntity<SearchController.SearchResponse> response = controller.search(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().totalResults());
        verify(vectorIngestTaskRepository, never()).findCandidateIdsByStatusIn(any(), any());
    }
}
