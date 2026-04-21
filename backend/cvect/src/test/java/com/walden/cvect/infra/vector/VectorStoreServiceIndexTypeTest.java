package com.walden.cvect.infra.vector;

import com.walden.cvect.infra.embedding.EmbeddingService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

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

    @Test
    @DisplayName("createHnswIndex should honor configured ivfflat index type")
    void createHnswIndexShouldHonorConfiguredIvfflatIndexType() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EntityManager entityManager = mock(EntityManager.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);

        VectorStoreConfig config = new VectorStoreConfig();
        config.setEnabled(true);
        config.setDimension(3);
        config.setIndexType("ivfflat");
        config.setTableName("resume_chunks");

        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class))).thenReturn(true);
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<ResultSetExtractor<String>>any(), anyString(), anyString()))
                .thenReturn(null);

        VectorStoreService service = new VectorStoreService(jdbcTemplate, entityManager, embeddingService, config);

        service.createHnswIndex();

        verify(jdbcTemplate).execute("DROP INDEX IF EXISTS idx_resume_chunks_embedding");
        var sqlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeastOnce()).execute(sqlCaptor.capture());
        assertThat(sqlCaptor.getAllValues())
                .anyMatch(sql -> sql.contains("USING ivfflat"))
                .anyMatch(sql -> sql.toLowerCase(Locale.ROOT).contains("create index"));
        assertThat(sqlCaptor.getAllValues())
                .noneMatch(sql -> sql.contains("WITH (m ="));
    }
}
