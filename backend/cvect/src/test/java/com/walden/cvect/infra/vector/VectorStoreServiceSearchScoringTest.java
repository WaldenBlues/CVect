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
import static org.mockito.Mockito.mock;
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
    @DisplayName("scoreCandidates should keep the best score per chunk type and skip invalid rows")
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
        when(jdbcTemplate.queryForList(anyString(), anyString())).thenReturn(List.of(
                Map.of(
                        "candidate_id", candidateId,
                        "chunk_type", ChunkType.EXPERIENCE.name(),
                        "score", 0.35f),
                Map.of(
                        "candidate_id", candidateId,
                        "chunk_type", ChunkType.EXPERIENCE.name(),
                        "score", 0.9f),
                Map.of(
                        "candidate_id", candidateId,
                        "chunk_type", ChunkType.SKILL.name(),
                        "score", 0.4d),
                Map.of(
                        "candidate_id", UUID.randomUUID(),
                        "chunk_type", "UNKNOWN",
                        "score", 1.0f)));

        VectorStoreService service = new VectorStoreService(jdbcTemplate, entityManager, embeddingService, config);
        Map<UUID, VectorStoreService.CandidateScoreBreakdown> scores =
                service.scoreCandidates(new float[] {0.1f, 0.2f, 0.3f}, List.of());

        assertEquals(1, scores.size());
        assertEquals(candidateId, scores.keySet().iterator().next());
        VectorStoreService.CandidateScoreBreakdown breakdown = scores.values().iterator().next();
        assertEquals(0.9f, breakdown.experienceScore(), 0.0001f);
        assertEquals(0.4f, breakdown.skillScore(), 0.0001f);
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

        when(jdbcTemplate.queryForObject(anyString(), any(Class.class))).thenReturn(true);

        VectorStoreService service = new VectorStoreService(jdbcTemplate, entityManager, embeddingService, config);

        assertThrows(IllegalArgumentException.class,
                () -> service.save(UUID.randomUUID(), ChunkType.EXPERIENCE, null));
    }
}
