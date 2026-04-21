package com.walden.cvect.infra.vector;

import com.walden.cvect.infra.embedding.EmbeddingService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("VectorStoreService index type regression tests")
class VectorStoreServiceIndexTypeTest {

    private static final String TABLE_NAME = "resume_chunks";
    private static final String INDEX_NAME = "idx_resume_chunks_embedding";
    private static final int DIMENSION = 3;

    @Test
    @DisplayName("createVectorIndex should honor configured ivfflat index type")
    void createVectorIndexShouldHonorConfiguredIvfflatIndexType() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        VectorStoreService service = newService(jdbcTemplate, newConfig("ivfflat", "cosine"), null);
        service.createVectorIndex();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeastOnce()).execute(sqlCaptor.capture());
        assertThat(sqlCaptor.getAllValues())
                .anyMatch(sql -> sql.contains("USING ivfflat"))
                .anyMatch(sql -> sql.toLowerCase(Locale.ROOT).contains("create index"))
                .noneMatch(sql -> sql.contains("WITH (m ="));
    }

    @Test
    @DisplayName("createVectorIndex should use default hnsw SQL with HNSW options")
    void createVectorIndexShouldUseDefaultHnswSql() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        VectorStoreService service = newService(jdbcTemplate, newConfig("hnsw", "cosine"), null);
        service.createVectorIndex();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeastOnce()).execute(sqlCaptor.capture());
        assertThat(sqlCaptor.getAllValues())
                .anyMatch(sql -> sql.contains("USING hnsw"))
                .anyMatch(sql -> sql.contains("WITH (m = 16, ef_construction = 64)"))
                .noneMatch(sql -> sql.contains("USING ivfflat"));
    }

    @Test
    @DisplayName("constructor should rebuild when an existing index has the wrong type")
    void ensureIndexCompatibilityShouldRebuildWhenExistingIndexTypeDiffers() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        String existingIndex = buildIndexDef("hnsw", "vector_cosine_ops");
        newService(jdbcTemplate, newConfig("ivfflat", "cosine"), existingIndex);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeastOnce()).execute(sqlCaptor.capture());
        List<String> sqls = sqlCaptor.getAllValues();

        assertThat(sqls)
                .anyMatch(sql -> sql.equals("DROP INDEX IF EXISTS " + INDEX_NAME))
                .anyMatch(sql -> sql.contains("USING ivfflat"))
                .anyMatch(sql -> sql.contains("vector_cosine_ops"))
                .noneMatch(sql -> sql.contains("WITH (m ="));
    }

    @Test
    @DisplayName("constructor should rebuild when the existing index opclass differs")
    void ensureIndexCompatibilityShouldRebuildWhenExistingIndexOpClassDiffers() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        String existingIndex = buildIndexDef("ivfflat", "vector_cosine_ops");
        newService(jdbcTemplate, newConfig("ivfflat", "l2"), existingIndex);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeastOnce()).execute(sqlCaptor.capture());

        assertThat(sqlCaptor.getAllValues())
                .anyMatch(sql -> sql.equals("DROP INDEX IF EXISTS " + INDEX_NAME))
                .anyMatch(sql -> sql.contains("USING ivfflat"))
                .anyMatch(sql -> sql.contains("vector_l2_ops"));
    }

    private static VectorStoreService newService(
            JdbcTemplate jdbcTemplate,
            VectorStoreConfig config,
            String existingIndexDef) {
        EntityManager entityManager = mock(EntityManager.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);

        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class))).thenReturn(true);
        if (existingIndexDef == null) {
            when(jdbcTemplate.query(
                    anyString(),
                    org.mockito.ArgumentMatchers.<ResultSetExtractor<String>>any(),
                    anyString(),
                    anyString())).thenReturn(null);
        } else {
            when(jdbcTemplate.query(
                    anyString(),
                    org.mockito.ArgumentMatchers.<ResultSetExtractor<String>>any(),
                    anyString(),
                    anyString())).thenReturn(existingIndexDef);
        }

        return new VectorStoreService(jdbcTemplate, entityManager, embeddingService, config);
    }

    private static VectorStoreConfig newConfig(String indexType, String metric) {
        VectorStoreConfig config = new VectorStoreConfig();
        config.setEnabled(true);
        config.setDimension(DIMENSION);
        config.setIndexType(indexType);
        config.setMetric(metric);
        config.setTableName(TABLE_NAME);
        return config;
    }

    private static String buildIndexDef(String indexType, String opClass) {
        return "CREATE INDEX " + INDEX_NAME
                + " ON public." + TABLE_NAME
                + " USING " + indexType
                + " ((REPLACE(REPLACE(embedding, '{', '['), '}', ']')::vector(" + DIMENSION + ")) "
                + opClass + ")";
    }
}
