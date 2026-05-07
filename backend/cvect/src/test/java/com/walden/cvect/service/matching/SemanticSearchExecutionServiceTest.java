package com.walden.cvect.service.matching;

import com.walden.cvect.infra.vector.VectorStoreService;
import com.walden.cvect.model.ChunkType;
import com.walden.cvect.model.TenantConstants;
import com.walden.cvect.repository.CandidateJpaRepository;
import com.walden.cvect.repository.VectorIngestTaskJpaRepository;
import com.walden.cvect.security.CurrentUserService;
import com.walden.cvect.security.DataScopeService;
import com.walden.cvect.web.controller.search.SearchController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SemanticSearchExecutionService unit tests")
class SemanticSearchExecutionServiceTest {

    @Mock
    private VectorStoreService vectorStore;

    @Mock
    private SearchQueryEmbeddingCacheService queryEmbeddingCache;

    @Mock
    private VectorIngestTaskJpaRepository vectorIngestTaskRepository;

    @Mock
    private CandidateJpaRepository candidateRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private DataScopeService dataScopeService;

    private SemanticSearchExecutionService service;

    @BeforeEach
    void setUp() {
        service = new SemanticSearchExecutionService(
                vectorStore,
                queryEmbeddingCache,
                vectorIngestTaskRepository,
                candidateRepository,
                currentUserService,
                dataScopeService);
    }

    @Test
    @DisplayName("shouldReturnAggregatedSearchResultsWhenRequestIsValid")
    void shouldReturnAggregatedSearchResultsWhenRequestIsValid() {
        UUID candidateId = UUID.randomUUID();
        float[] embedding = new float[] {0.1f, 0.2f};
        when(currentUserService.currentTenantId()).thenReturn(TenantConstants.DEFAULT_TENANT_ID);
        when(dataScopeService.hasTenantWideScope()).thenReturn(true);
        when(queryEmbeddingCache.get("Java backend role")).thenReturn(embedding);
        when(vectorStore.searchVisible(
                eq(embedding),
                eq(28),
                eq(new VectorStoreService.SearchScope(TenantConstants.DEFAULT_TENANT_ID, null)),
                eq(0.35f),
                any(ChunkType[].class)))
                .thenReturn(List.of(new VectorStoreService.SearchResult(
                        UUID.randomUUID(),
                        candidateId,
                        ChunkType.EXPERIENCE,
                        "Led Java search platform delivery",
                        0.13f)));

        SearchController.SearchResponse response = service.search(new SearchController.SearchRequest(
                "Java backend role",
                7,
                true,
                false,
                null,
                null,
                false));

        assertThat(response.totalResults()).isEqualTo(1);
        assertThat(response.requested()).isEqualTo(7);
        assertThat(response.candidates()).singleElement().satisfies(candidateMatch -> {
            assertThat(candidateMatch.candidateId()).isEqualTo(candidateId);
            assertThat(candidateMatch.score()).isEqualTo(0.87f);
            assertThat(candidateMatch.matchedChunks()).singleElement().satisfies(chunk -> {
                assertThat(chunk.chunkType()).isEqualTo("EXPERIENCE");
                assertThat(chunk.content()).contains("Java search platform");
            });
        });

        ArgumentCaptor<ChunkType[]> chunkTypesCaptor = ArgumentCaptor.forClass(ChunkType[].class);
        verify(vectorStore).searchVisible(
                eq(embedding),
                eq(28),
                eq(new VectorStoreService.SearchScope(TenantConstants.DEFAULT_TENANT_ID, null)),
                eq(0.35f),
                chunkTypesCaptor.capture());
        assertThat(chunkTypesCaptor.getValue()).containsExactly(ChunkType.EXPERIENCE);
        verifyNoInteractions(candidateRepository);
    }

    @Test
    @DisplayName("shouldReturnEmptyListWhenNoMatchesAreFound")
    void shouldReturnEmptyListWhenNoMatchesAreFound() {
        float[] embedding = new float[] {0.4f, 0.5f};
        when(currentUserService.currentTenantId()).thenReturn(TenantConstants.DEFAULT_TENANT_ID);
        when(dataScopeService.hasTenantWideScope()).thenReturn(true);
        when(queryEmbeddingCache.get("No match role")).thenReturn(embedding);
        when(vectorStore.searchVisible(
                eq(embedding),
                eq(20),
                eq(new VectorStoreService.SearchScope(TenantConstants.DEFAULT_TENANT_ID, null)),
                eq(0.35f),
                org.mockito.ArgumentMatchers.<ChunkType[]>isNull()))
                .thenReturn(List.of());

        SearchController.SearchResponse response = service.search(new SearchController.SearchRequest(
                "No match role",
                5,
                false,
                false,
                null,
                null,
                false));

        assertThat(response.totalResults()).isZero();
        assertThat(response.requested()).isEqualTo(5);
        assertThat(response.candidates()).isEmpty();
        verifyNoInteractions(candidateRepository);
    }

    @Test
    @DisplayName("shouldPropagateEmbeddingFailuresWithExplicitException")
    void shouldPropagateEmbeddingFailuresWithExplicitException() {
        when(queryEmbeddingCache.get("Broken embedding request"))
                .thenThrow(new IllegalStateException("Embedding service unavailable"));

        assertThatThrownBy(() -> service.search(new SearchController.SearchRequest(
                "Broken embedding request",
                5,
                false,
                false,
                null,
                null,
                false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Embedding service unavailable");

        verify(vectorStore, never()).searchVisible(any(float[].class), anyInt(), any(), anyFloat(), any(ChunkType[].class));
    }

    @Test
    @DisplayName("shouldPassExpandedTopKToVectorStore")
    void shouldPassExpandedTopKToVectorStore() {
        float[] embedding = new float[] {0.8f};
        when(currentUserService.currentTenantId()).thenReturn(TenantConstants.DEFAULT_TENANT_ID);
        when(dataScopeService.hasTenantWideScope()).thenReturn(true);
        when(queryEmbeddingCache.get("Large search")).thenReturn(embedding);
        when(vectorStore.searchVisible(
                eq(embedding),
                eq(800),
                eq(new VectorStoreService.SearchScope(TenantConstants.DEFAULT_TENANT_ID, null)),
                eq(0.35f),
                any(ChunkType[].class)))
                .thenReturn(List.of());

        SearchController.SearchRequest request = new SearchController.SearchRequest(
                "Large search",
                5000,
                false,
                true,
                null,
                null,
                false);

        SearchController.SearchResponse response = service.search(request);

        assertThat(request.topK()).isEqualTo(200);
        assertThat(response.requested()).isEqualTo(200);
        verify(vectorStore).searchVisible(
                eq(embedding),
                eq(800),
                eq(new VectorStoreService.SearchScope(TenantConstants.DEFAULT_TENANT_ID, null)),
                eq(0.35f),
                any(ChunkType[].class));
        verifyNoInteractions(candidateRepository);
    }

    @Test
    @DisplayName("shouldUseCreatorScopedSearchWhenTenantWideScopeIsUnavailable")
    void shouldUseCreatorScopedSearchWhenTenantWideScopeIsUnavailable() {
        UUID userId = UUID.randomUUID();
        float[] embedding = new float[] {0.2f, 0.3f};
        when(currentUserService.currentTenantId()).thenReturn(TenantConstants.DEFAULT_TENANT_ID);
        when(dataScopeService.hasTenantWideScope()).thenReturn(false);
        when(dataScopeService.currentUserIdOrNull()).thenReturn(userId);
        when(queryEmbeddingCache.get("Scoped search")).thenReturn(embedding);
        when(vectorStore.searchVisible(
                eq(embedding),
                eq(20),
                eq(new VectorStoreService.SearchScope(TenantConstants.DEFAULT_TENANT_ID, userId)),
                eq(0.35f),
                org.mockito.ArgumentMatchers.<ChunkType[]>isNull()))
                .thenReturn(List.of());

        SearchController.SearchResponse response = service.search(new SearchController.SearchRequest(
                "Scoped search",
                5,
                false,
                false,
                null,
                null,
                false));

        assertThat(response.totalResults()).isZero();
        verify(vectorStore).searchVisible(
                eq(embedding),
                eq(20),
                eq(new VectorStoreService.SearchScope(TenantConstants.DEFAULT_TENANT_ID, userId)),
                eq(0.35f),
                org.mockito.ArgumentMatchers.<ChunkType[]>isNull());
        verifyNoInteractions(candidateRepository);
    }

    @Test
    @DisplayName("shouldFilterLowSimilarityCandidatesBelowThreshold")
    void shouldFilterLowSimilarityCandidatesBelowThreshold() {
        UUID highScoreCandidateId = UUID.randomUUID();
        UUID lowScoreCandidateId = UUID.randomUUID();
        float[] embedding = new float[] {0.5f, 0.6f};
        when(currentUserService.currentTenantId()).thenReturn(TenantConstants.DEFAULT_TENANT_ID);
        when(dataScopeService.hasTenantWideScope()).thenReturn(true);
        when(queryEmbeddingCache.get("Threshold search")).thenReturn(embedding);
        when(vectorStore.searchVisible(
                eq(embedding),
                eq(20),
                eq(new VectorStoreService.SearchScope(TenantConstants.DEFAULT_TENANT_ID, null)),
                eq(0.35f),
                org.mockito.ArgumentMatchers.<ChunkType[]>isNull()))
                .thenReturn(List.of(
                        new VectorStoreService.SearchResult(
                                UUID.randomUUID(),
                                highScoreCandidateId,
                                ChunkType.EXPERIENCE,
                                "strong",
                                0.20f),
                        new VectorStoreService.SearchResult(
                                UUID.randomUUID(),
                                lowScoreCandidateId,
                                ChunkType.EXPERIENCE,
                                "weak",
                                0.80f)));

        SearchController.SearchResponse response = service.search(new SearchController.SearchRequest(
                "Threshold search",
                5,
                false,
                false,
                null,
                null,
                false));

        assertThat(response.totalResults()).isEqualTo(1);
        assertThat(response.candidates()).singleElement()
                .extracting(SearchController.CandidateMatch::candidateId)
                .isEqualTo(highScoreCandidateId);
    }
}
