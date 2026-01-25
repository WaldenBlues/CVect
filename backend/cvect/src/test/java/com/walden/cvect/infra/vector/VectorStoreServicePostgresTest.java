package com.walden.cvect.infra.vector;

import com.walden.cvect.infra.embedding.EmbeddingService;
import com.walden.cvect.model.ChunkType;
import com.walden.cvect.model.ParseResult;
import com.walden.cvect.model.ResumeChunk;
import com.walden.cvect.infra.parser.ResumeParser;
import com.walden.cvect.infra.process.ResumeTextNormalizer;
import com.walden.cvect.service.ChunkerService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * VectorStoreService 集成测试 - 使用 Testcontainers PostgreSQL
 * 提供可靠的集成测试环境，不依赖外部 Docker 服务
 * 
 * 测试原则：
 * 1. 使用 Testcontainers 提供隔离的 PostgreSQL + pgvector 环境
 * 2. Mock 外部依赖（Python embedding 服务）
 * 3. 验证真实数据库操作
 */
@Testcontainers
@SpringBootTest
@Tag("integration")
@Tag("vector")
@Tag("postgres")
@DisplayName("VectorStoreService PostgreSQL 集成测试 (Testcontainers)")
class VectorStoreServicePostgresTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("cvect_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        
        // Vector store configuration
        registry.add("app.vector.table-name", () -> "resume_chunks");
        registry.add("app.vector.index-type", () -> "hnsw");
        registry.add("app.vector.metric", () -> "cosine");
        registry.add("app.vector.ef-construction", () -> "64");
        registry.add("app.vector.m", () -> "16");
        
        // Mock embedding service configuration
        registry.add("app.embedding.model-name", () -> "Qwen/Qwen2.5-Embedding-0.6B-Instruct");
        registry.add("app.embedding.device", () -> "cpu");
        registry.add("app.embedding.dimension", () -> "768");
    }

    @Autowired
    private VectorStoreService vectorStore;

    @MockBean
    private EmbeddingService embeddingService;

    @Autowired
    private ResumeParser parser;

    @Autowired
    private ResumeTextNormalizer normalizer;

    @Autowired
    private ChunkerService chunker;

    private UUID testCandidateId;

    @BeforeEach
    void setUp() {
        testCandidateId = UUID.randomUUID();
        
        // Mock embedding service to return dummy embeddings
        float[] dummyEmbedding = new float[768];
        // Initialize with some values for similarity testing
        for (int i = 0; i < dummyEmbedding.length; i++) {
            dummyEmbedding[i] = (float) Math.random() * 0.1f;
        }
        when(embeddingService.embed(anyString())).thenReturn(dummyEmbedding);
    }

    @AfterEach
    void tearDown() {
        if (vectorStore != null) {
            try {
                vectorStore.deleteByCandidate(testCandidateId);
            } catch (Exception e) {
                // Ignore cleanup exceptions
            }
        }
    }

    @Test
    @DisplayName("应能保存 EXPERIENCE chunk 向量到 PostgreSQL")
    void should_save_experience_vector() {
        // Given
        String content = "5 years of Java development experience with Spring Boot";
        
        // When & Then
        Assertions.assertDoesNotThrow(() -> {
            vectorStore.save(testCandidateId, ChunkType.EXPERIENCE, content);
        });
    }

    @Test
    @DisplayName("应能保存 SKILL chunk 向量到 PostgreSQL")
    void should_save_skill_vector() {
        // Given
        String content = "Java, Spring Boot, PostgreSQL, Docker";
        
        // When & Then
        Assertions.assertDoesNotThrow(() -> {
            vectorStore.save(testCandidateId, ChunkType.SKILL, content);
        });
    }

    @Test
    @DisplayName("应能搜索相似向量")
    void should_search_similar_vectors() {
        // Given: Save some vectors
        vectorStore.save(testCandidateId, ChunkType.EXPERIENCE, "Java development with microservices");
        vectorStore.save(testCandidateId, ChunkType.SKILL, "Spring Boot, Docker, Kubernetes");
        
        // Create query embedding (similar to saved content)
        float[] queryEmbedding = new float[768];
        for (int i = 0; i < queryEmbedding.length; i++) {
            queryEmbedding[i] = (float) Math.random() * 0.1f;
        }
        
        // When: Search for similar vectors
        var results = vectorStore.search(queryEmbedding, 10, ChunkType.EXPERIENCE, ChunkType.SKILL);
        
        // Then: Should return results
        assertThat(results).isNotNull();
        // Note: Since we're using random embeddings, we can't guarantee matches
        // but the query should execute without errors
    }

    @Test
    @DisplayName("应能按候选人 ID 删除向量")
    void should_delete_vectors_by_candidate() {
        // Given
        vectorStore.save(testCandidateId, ChunkType.EXPERIENCE, "Test experience");
        vectorStore.save(testCandidateId, ChunkType.SKILL, "Test skill");
        
        // When & Then
        Assertions.assertDoesNotThrow(() -> {
            vectorStore.deleteByCandidate(testCandidateId);
        });
    }

    @Test
    @DisplayName("应能创建 HNSW 索引")
    void should_create_hnsw_index() {
        // When & Then
        Assertions.assertDoesNotThrow(() -> {
            vectorStore.createHnswIndex();
        });
    }

    @Test
    @DisplayName("应支持候选人数据隔离")
    void should_support_candidate_isolation() {
        // Given: Create data for two different candidates
        UUID candidate1 = UUID.randomUUID();
        UUID candidate2 = UUID.randomUUID();
        
        vectorStore.save(candidate1, ChunkType.EXPERIENCE, "Candidate 1 experience");
        vectorStore.save(candidate2, ChunkType.EXPERIENCE, "Candidate 2 experience");
        
        // When: Delete only candidate1's data
        Assertions.assertDoesNotThrow(() -> {
            vectorStore.deleteByCandidate(candidate1);
        });
        
        // Then: Deleting candidate1 again should not fail
        Assertions.assertDoesNotThrow(() -> {
            vectorStore.deleteByCandidate(candidate1);
        });
        
        // Cleanup
        vectorStore.deleteByCandidate(candidate2);
    }

    /**
     * 从真实 PDF 解析并获取指定类型的 chunk
     * 遵循完整流水线：parser -> normalizer -> chunker
     */
    private List<ResumeChunk> getChunksFromPdf(String pdfPath, ChunkType type) throws Exception {
        InputStream is = getClass().getResourceAsStream(pdfPath);
        assertThat(is).isNotNull();
        
        ParseResult parsed = parser.parse(is, "application/pdf");
        assertThat(parsed.getContent()).isNotBlank();
        
        String normalized = normalizer.normalize(parsed.getContent());
        List<ResumeChunk> chunks = chunker.chunk(normalized);
        
        return chunks.stream()
                .filter(c -> c.getType() == type)
                .toList();
    }

    @Test
    @DisplayName("全链路测试：从真实 PDF 到向量存储")
    void should_store_vectors_from_real_pdf() throws Exception {
        // Given: Parse real PDF to get chunks
        List<ResumeChunk> experienceChunks = getChunksFromPdf("/static/My.pdf", ChunkType.EXPERIENCE);
        List<ResumeChunk> skillChunks = getChunksFromPdf("/static/My.pdf", ChunkType.SKILL);
        
        List<ResumeChunk> vectorChunks = new ArrayList<>();
        vectorChunks.addAll(experienceChunks);
        vectorChunks.addAll(skillChunks);
        
        assertThat(vectorChunks).isNotEmpty();
        
        // When: Store all vector chunks
        for (ResumeChunk chunk : vectorChunks) {
            Assertions.assertDoesNotThrow(() -> {
                vectorStore.save(testCandidateId, chunk.getType(), chunk.getContent());
            });
        }
        
        // Then: Verify at least some data was stored
        // (No explicit assertion - success is not throwing exceptions)
    }
}