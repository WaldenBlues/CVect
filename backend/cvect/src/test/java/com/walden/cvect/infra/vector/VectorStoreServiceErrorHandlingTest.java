package com.walden.cvect.infra.vector;

import com.walden.cvect.infra.embedding.EmbeddingService;
import com.walden.cvect.model.ChunkType;
import com.walden.cvect.model.entity.Candidate;
import com.walden.cvect.repository.CandidateJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * VectorStoreService 错误处理测试
 * 测试原则：验证外部服务失败时的优雅降级和错误处理
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.vector.enabled=true",
    "app.embedding.dimension=1024",
    "app.vector.dimension=1024"
})
@Tag("integration")
@DisplayName("VectorStoreService 错误处理测试")
class VectorStoreServiceErrorHandlingTest {

    @Autowired
    private VectorStoreService vectorStore;

    @MockBean
    private EmbeddingService embeddingService;

    @Autowired
    private CandidateJpaRepository candidateRepository;

    @Test
    @DisplayName("当向量存储不可用时，save 方法应返回 false 并跳过 embedding 服务")
    void should_return_false_when_vector_store_is_unavailable() {
        // Given
        UUID candidateId = createCandidateId("vector-error-1");
        String content = "Java developer experience";

        // When & Then: 向量存储不可用时会直接跳过保存
        assertThat(vectorStore.save(candidateId, ChunkType.EXPERIENCE, content)).isFalse();
        verifyNoInteractions(embeddingService);
    }

    @Test
    @DisplayName("当向量存储不可用时，内容保存应返回 false")
    void should_return_false_for_content_save_when_vector_store_is_unavailable() {
        // Given
        UUID candidateId = createCandidateId("vector-error-2");
        String content = "Test content";

        // When & Then: 向量存储不可用时会直接跳过保存
        assertThat(vectorStore.save(candidateId, ChunkType.EXPERIENCE, content)).isFalse();
        verifyNoInteractions(embeddingService);
    }

    @Test
    @DisplayName("当 pgvector 不可用时，搜索应抛出 IllegalStateException")
    void should_throw_when_search_is_called_without_pgvector() {
        // Given
        float[] invalidEmbedding = new float[0];

        // When & Then: pgvector 不可用时应明确失败
        assertThatThrownBy(() ->
                vectorStore.search(invalidEmbedding, 10, ChunkType.EXPERIENCE)
        ).isInstanceOf(IllegalStateException.class)
                .hasMessage("pgvector extension is unavailable");
    }

    @Test
    @DisplayName("删除不存在的候选人不应抛出异常")
    void should_not_throw_exception_when_deleting_nonexistent_candidate() {
        // Given
        UUID nonExistentCandidateId = UUID.randomUUID();

        // When & Then
        assertThatCode(() -> vectorStore.deleteByCandidate(nonExistentCandidateId))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("当 pgvector 不可用时，创建向量索引应直接跳过")
    void should_skip_index_creation_when_pgvector_is_unavailable() {
        assertThatCode(() -> vectorStore.createVectorIndex())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("当向量存储不可用时，chunk 保存应返回 false")
    void should_return_false_when_vector_store_is_unavailable_for_chunk_saves() {
        // Given
        UUID candidateId = createCandidateId("vector-error-3");
        String content = "Test content";

        // When & Then: 向量存储不可用时应跳过保存，不抛异常
        assertThat(vectorStore.save(candidateId, ChunkType.EXPERIENCE, content)).isFalse();
        assertThat(vectorStore.save(candidateId, ChunkType.SKILL, content)).isFalse();
        verifyNoInteractions(embeddingService);
    }

    @Test
    @DisplayName("当向量存储不可用时，空内容字符串也应返回 false")
    void should_return_false_for_empty_content_string() {
        // Given
        UUID candidateId = createCandidateId("vector-error-4");
        String emptyContent = "";

        // When & Then: 向量存储不可用时会直接跳过保存
        assertThat(vectorStore.save(candidateId, ChunkType.EXPERIENCE, emptyContent)).isFalse();
        verifyNoInteractions(embeddingService);
    }

    @Test
    @DisplayName("当向量存储不可用时，超长内容字符串也应返回 false")
    void should_return_false_for_very_long_content_string() {
        // Given
        UUID candidateId = createCandidateId("vector-error-5");
        StringBuilder longContent = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            longContent.append("Very long resume content line ").append(i).append(". ");
        }

        // When & Then: 向量存储不可用时会直接跳过保存
        assertThat(vectorStore.save(candidateId, ChunkType.EXPERIENCE, longContent.toString())).isFalse();
        verifyNoInteractions(embeddingService);
    }

    private UUID createCandidateId(String namePrefix) {
        Candidate candidate = new Candidate(
                namePrefix + ".pdf",
                UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""),
                namePrefix,
                null,
                "application/pdf",
                64L,
                64,
                false);
        return candidateRepository.save(candidate).getId();
    }
}
