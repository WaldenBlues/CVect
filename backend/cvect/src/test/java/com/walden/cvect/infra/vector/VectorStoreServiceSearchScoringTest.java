package com.walden.cvect.infra.vector;

import com.walden.cvect.infra.embedding.EmbeddingService;
import com.walden.cvect.model.ChunkType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("VectorStoreService search scoring regression tests")
class VectorStoreServiceSearchScoringTest {

    @Test
    @DisplayName("search should keep raw distance and compute bounded score once")
    void searchShouldKeepRawDistanceAndComputeScore() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EntityManager entityManager = mock(EntityManager.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);

        VectorStoreConfig config = new VectorStoreConfig();
        config.setEnabled(true);
        config.setDimension(3);
        config.setTableName("resume_chunks");

        when(jdbcTemplate.queryForObject(anyString(), any(Class.class))).thenReturn(true);

        UUID rowId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        when(jdbcTemplate.queryForList(anyString(), anyString(), anyInt())).thenReturn(List.of(
                Map.of(
                        "id", rowId,
                        "candidate_id", candidateId,
                        "chunk_type", ChunkType.EXPERIENCE.name(),
                        "content", "backend java",
                        "distance", 0.25f)));

        VectorStoreService service = new VectorStoreService(jdbcTemplate, entityManager, embeddingService, config);

        List<VectorStoreService.SearchResult> results = service.search(new float[] {0.1f, 0.2f, 0.3f}, 5);
        assertEquals(1, results.size());
        assertEquals(0.25f, results.get(0).distance(), 0.0001f);
        assertEquals(0.75f, results.get(0).score(), 0.0001f);
    }

    @Test
    @DisplayName("score should be clamped to zero when distance exceeds one")
    void scoreShouldBeClampedToZeroWhenDistanceExceedsOne() {
        VectorStoreService.SearchResult result = new VectorStoreService.SearchResult(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ChunkType.SKILL,
                "k8s",
                1.4f);
        assertEquals(0.0f, result.score(), 0.0001f);
    }

    @Test
    @DisplayName("deleteByJobDescription should use the H2-compatible subquery form")
    void deleteByJobDescriptionShouldUseSubqueryForm() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EntityManager entityManager = mock(EntityManager.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);

        VectorStoreConfig config = new VectorStoreConfig();
        config.setEnabled(true);
        config.setDimension(3);
        config.setTableName("resume_chunks");

        when(jdbcTemplate.queryForObject(anyString(), any(Class.class))).thenReturn(true);

        VectorStoreService service = new VectorStoreService(jdbcTemplate, entityManager, embeddingService, config);
        UUID jobDescriptionId = UUID.randomUUID();

        service.deleteByJobDescription(jobDescriptionId);

        verify(jdbcTemplate).update(
                "DELETE FROM resume_chunks WHERE candidate_id IN (SELECT id FROM candidates WHERE jd_id = ?)",
                jobDescriptionId);
    }

    @Test
    @DisplayName("search should accept non-string chunk_type values from JDBC rows")
    void searchShouldAcceptNonStringChunkTypeValues() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EntityManager entityManager = mock(EntityManager.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);

        VectorStoreConfig config = new VectorStoreConfig();
        config.setEnabled(true);
        config.setDimension(3);
        config.setTableName("resume_chunks");

        when(jdbcTemplate.queryForObject(anyString(), any(Class.class))).thenReturn(true);
        when(jdbcTemplate.queryForList(anyString(), anyString(), anyInt())).thenReturn(List.of(
                Map.of(
                        "id", UUID.randomUUID(),
                        "candidate_id", UUID.randomUUID(),
                        "chunk_type", new Object() {
                            @Override
                            public String toString() {
                                return "skill";
                            }
                        },
                        "content", "vector skill",
                        "distance", 0.1f)));

        VectorStoreService service = new VectorStoreService(jdbcTemplate, entityManager, embeddingService, config);
        List<VectorStoreService.SearchResult> results = service.search(new float[] {0.1f, 0.2f, 0.3f}, 1);

        assertEquals(1, results.size());
        assertEquals(ChunkType.SKILL, results.get(0).chunkType());
    }

    @Test
    @DisplayName("search should skip invalid chunk_type rows instead of failing whole query")
    void searchShouldSkipInvalidChunkTypeRows() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EntityManager entityManager = mock(EntityManager.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);

        VectorStoreConfig config = new VectorStoreConfig();
        config.setEnabled(true);
        config.setDimension(3);
        config.setTableName("resume_chunks");

        when(jdbcTemplate.queryForObject(anyString(), any(Class.class))).thenReturn(true);
        when(jdbcTemplate.queryForList(anyString(), anyString(), anyInt())).thenReturn(List.of(
                Map.of(
                        "id", UUID.randomUUID(),
                        "candidate_id", UUID.randomUUID(),
                        "chunk_type", "UNKNOWN_TYPE",
                        "content", "bad row",
                        "distance", 0.2f),
                Map.of(
                        "id", UUID.randomUUID(),
                        "candidate_id", UUID.randomUUID(),
                        "chunk_type", "EXPERIENCE",
                        "content", "good row",
                        "distance", 0.3f)));

        VectorStoreService service = new VectorStoreService(jdbcTemplate, entityManager, embeddingService, config);
        List<VectorStoreService.SearchResult> results = service.search(new float[] {0.1f, 0.2f, 0.3f}, 10);

        assertEquals(1, results.size());
        assertEquals(ChunkType.EXPERIENCE, results.get(0).chunkType());
    }

    @Test
    @DisplayName("scoreCandidates should keep the best score per chunk type for each candidate and skip invalid rows")
    void scoreCandidatesShouldKeepBestScoresAndSkipInvalidRows() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EntityManager entityManager = mock(EntityManager.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);

        VectorStoreConfig config = new VectorStoreConfig();
        config.setEnabled(true);
        config.setDimension(3);
        config.setTableName("resume_chunks");

        when(jdbcTemplate.queryForObject(anyString(), any(Class.class))).thenReturn(true);
        UUID candidateId = UUID.randomUUID();
        UUID otherCandidateId = UUID.randomUUID();
        when(jdbcTemplate.queryForList(anyString(), anyString())).thenReturn(List.of(
                Map.of(
                        "candidate_id", candidateId,
                        "chunk_type", ChunkType.EXPERIENCE.name(),
                        "score", 0.35f),
                Map.of(
                        "candidate_id", otherCandidateId,
                        "chunk_type", ChunkType.EXPERIENCE.name(),
                        "score", 0.6f),
                Map.of(
                        "candidate_id", candidateId,
                        "chunk_type", ChunkType.EXPERIENCE.name(),
                        "score", 0.9f),
                Map.of(
                        "candidate_id", otherCandidateId,
                        "chunk_type", ChunkType.SKILL.name(),
                        "score", 0.4d),
                Map.of(
                        "candidate_id", "not-a-uuid",
                        "chunk_type", ChunkType.EXPERIENCE.name(),
                        "score", 0.7f),
                Map.of(
                        "candidate_id", UUID.randomUUID(),
                        "chunk_type", "UNKNOWN",
                        "score", 1.0f)));

        VectorStoreService service = new VectorStoreService(jdbcTemplate, entityManager, embeddingService, config);
        Map<UUID, VectorStoreService.CandidateScoreBreakdown> scores =
                service.scoreCandidates(new float[] {0.1f, 0.2f, 0.3f}, List.of());

        assertEquals(2, scores.size());
        assertEquals(0.9f, scores.get(candidateId).experienceScore(), 0.0001f);
        assertEquals(0.0f, scores.get(candidateId).skillScore(), 0.0001f);
        assertEquals(0.6f, scores.get(otherCandidateId).experienceScore(), 0.0001f);
        assertEquals(0.4f, scores.get(otherCandidateId).skillScore(), 0.0001f);
    }

    @Test
    @DisplayName("scoreCandidates should skip rows with missing score values")
    void scoreCandidatesShouldSkipRowsWithMissingScoreValues() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EntityManager entityManager = mock(EntityManager.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);

        VectorStoreConfig config = new VectorStoreConfig();
        config.setEnabled(true);
        config.setDimension(3);
        config.setTableName("resume_chunks");

        when(jdbcTemplate.queryForObject(anyString(), any(Class.class))).thenReturn(true);
        UUID candidateId = UUID.randomUUID();
        Map<String, Object> invalidRow = new java.util.HashMap<>();
        invalidRow.put("candidate_id", candidateId);
        invalidRow.put("chunk_type", ChunkType.SKILL.name());
        invalidRow.put("score", null);
        when(jdbcTemplate.queryForList(anyString(), anyString())).thenReturn(List.of(
                Map.of(
                        "candidate_id", candidateId,
                        "chunk_type", ChunkType.EXPERIENCE.name(),
                        "score", 0.8f),
                invalidRow));

        VectorStoreService service = new VectorStoreService(jdbcTemplate, entityManager, embeddingService, config);
        Map<UUID, VectorStoreService.CandidateScoreBreakdown> scores =
                service.scoreCandidates(new float[] {0.1f, 0.2f, 0.3f}, List.of());

        assertEquals(1, scores.size());
        assertEquals(0.8f, scores.get(candidateId).experienceScore(), 0.0001f);
        assertEquals(0.0f, scores.get(candidateId).skillScore(), 0.0001f);
    }

    @Test
    @DisplayName("scoreCandidates should skip rows with non-finite score values")
    void scoreCandidatesShouldSkipRowsWithNonFiniteScoreValues() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EntityManager entityManager = mock(EntityManager.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);

        VectorStoreConfig config = new VectorStoreConfig();
        config.setEnabled(true);
        config.setDimension(3);
        config.setTableName("resume_chunks");

        when(jdbcTemplate.queryForObject(anyString(), any(Class.class))).thenReturn(true);
        UUID candidateId = UUID.randomUUID();
        when(jdbcTemplate.queryForList(anyString(), anyString())).thenReturn(List.of(
                Map.of(
                        "candidate_id", candidateId,
                        "chunk_type", ChunkType.EXPERIENCE.name(),
                        "score", 0.8f),
                Map.of(
                        "candidate_id", candidateId,
                        "chunk_type", ChunkType.SKILL.name(),
                        "score", Double.POSITIVE_INFINITY)));

        VectorStoreService service = new VectorStoreService(jdbcTemplate, entityManager, embeddingService, config);
        Map<UUID, VectorStoreService.CandidateScoreBreakdown> scores =
                service.scoreCandidates(new float[] {0.1f, 0.2f, 0.3f}, List.of());

        assertEquals(1, scores.size());
        assertEquals(0.8f, scores.get(candidateId).experienceScore(), 0.0001f);
        assertEquals(0.0f, scores.get(candidateId).skillScore(), 0.0001f);
    }

    @Test
    @DisplayName("save should reject null content with a validation error")
    void saveShouldRejectNullContent() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EntityManager entityManager = mock(EntityManager.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);

        VectorStoreConfig config = new VectorStoreConfig();
        config.setEnabled(true);
        config.setDimension(3);
        config.setTableName("resume_chunks");

        when(jdbcTemplate.queryForObject(anyString(), any(Class.class))).thenReturn(false);

        VectorStoreService service = new VectorStoreService(jdbcTemplate, entityManager, embeddingService, config);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.save(UUID.randomUUID(), ChunkType.EXPERIENCE, null));
        assertEquals("content must not be null", exception.getMessage());
        verify(embeddingService, never()).embed(anyString());
        verify(entityManager, never()).persist(any());
    }

    @Test
    @DisplayName("save should reject embedding dimension mismatch before persisting")
    void saveShouldRejectEmbeddingDimensionMismatch() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EntityManager entityManager = mock(EntityManager.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);

        VectorStoreConfig config = new VectorStoreConfig();
        config.setEnabled(true);
        config.setDimension(3);
        config.setTableName("resume_chunks");

        when(jdbcTemplate.queryForObject(anyString(), any(Class.class))).thenReturn(true);
        when(embeddingService.embed(anyString())).thenReturn(new float[] {0.1f, 0.2f});

        VectorStoreService service = new VectorStoreService(jdbcTemplate, entityManager, embeddingService, config);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.save(UUID.randomUUID(), ChunkType.EXPERIENCE, "backend java"));
        assertEquals("embedding dimension mismatch: expected=3, actual=2", exception.getMessage());
        verify(entityManager, never()).persist(any());
    }

    @Test
    @DisplayName("save should reject non-finite embedding values before persisting")
    void saveShouldRejectNonFiniteEmbeddingValues() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EntityManager entityManager = mock(EntityManager.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);

        VectorStoreConfig config = new VectorStoreConfig();
        config.setEnabled(true);
        config.setDimension(3);
        config.setTableName("resume_chunks");

        when(jdbcTemplate.queryForObject(anyString(), any(Class.class))).thenReturn(true);
        when(embeddingService.embed(anyString())).thenReturn(new float[] {0.1f, Float.NaN, 0.3f});

        VectorStoreService service = new VectorStoreService(jdbcTemplate, entityManager, embeddingService, config);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.save(UUID.randomUUID(), ChunkType.EXPERIENCE, "backend java"));
        assertEquals("embedding must contain only finite values", exception.getMessage());
        verify(entityManager, never()).persist(any());
    }

    @Test
    @DisplayName("search should reject non-finite query embeddings before executing SQL")
    void searchShouldRejectNonFiniteQueryEmbeddings() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EntityManager entityManager = mock(EntityManager.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);

        VectorStoreConfig config = new VectorStoreConfig();
        config.setEnabled(true);
        config.setDimension(3);
        config.setTableName("resume_chunks");

        when(jdbcTemplate.queryForObject(anyString(), any(Class.class))).thenReturn(true);

        VectorStoreService service = new VectorStoreService(jdbcTemplate, entityManager, embeddingService, config);
        clearInvocations(jdbcTemplate, entityManager, embeddingService);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.search(new float[] {0.1f, Float.NaN, 0.3f}, 5));
        assertEquals("queryEmbedding must contain only finite values", exception.getMessage());
        verifyNoInteractions(jdbcTemplate, entityManager, embeddingService);
    }
}
