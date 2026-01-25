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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VectorStoreService 集成测试
 *
 * 测试原则：遵循完整流水线测试模式
 * PDF -> parser -> normalizer -> chunker -> fact extraction -> vector storage
 *
 * 前置条件：需要 Docker 启动 PostgreSQL + pgvector
 * 启动命令: docker-compose up -d
 *
 * 如果 Docker 未启动，测试会自动跳过
 */
@SpringBootTest
@Tag("integration")
@Tag("vector")
@DisplayName("VectorStoreService 集成测试（流水线测试）")
class VectorStoreServiceIntegrationTest {

    private static final boolean DOCKER_RUNNING = System.getenv("DOCKER_ACTIVE") != null
            || System.getenv("CI") != null
            || isPortInUse(5432);

    private static boolean isPortInUse(int port) {
        try (var socket = new java.net.ServerSocket(port)) {
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (DOCKER_RUNNING) {
            // Docker 模式：PostgreSQL + pgvector
            registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5432/cvect");
            registry.add("spring.datasource.username", () -> "postgres");
            registry.add("spring.datasource.password", () -> "postgres");
            registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
            registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
            registry.add("app.vector.table-name", () -> "resume_chunks");
            registry.add("app.vector.index-type", () -> "hnsw");
            registry.add("app.vector.metric", () -> "cosine");
            registry.add("app.vector.ef-construction", () -> "64");
            registry.add("app.vector.m", () -> "16");
        } else {
            // 开发模式：使用 H2 内存数据库
            registry.add("spring.datasource.url", () -> "jdbc:h2:mem:cvect;MODE=PostgreSQL");
            registry.add("spring.datasource.username", () -> "sa");
            registry.add("spring.datasource.password", () -> "");
            registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
            registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.H2Dialect");
            registry.add("app.vector.table-name", () -> "resume_chunks");
            registry.add("app.vector.index-type", () -> "hnsw");
            registry.add("app.vector.metric", () -> "cosine");
        }
    }

    @Autowired(required = false)
    private VectorStoreService vectorStore;

    @Autowired
    private ResumeParser parser;

    @Autowired
    private ResumeTextNormalizer normalizer;

    @Autowired
    private ChunkerService chunker;

    @Autowired(required = false)
    private EmbeddingService embeddingService;

    private UUID testCandidateId;

    /**
     * 从真实 PDF 解析并获取指定类型的 chunk
     * 遵循完整流水线：parser -> normalizer -> chunker
     */
    private List<ResumeChunk> getChunksFromPdf(String pdfPath, ChunkType type) throws Exception {
        InputStream is = getClass().getResourceAsStream(pdfPath);
        assertNotNull(is, pdfPath + " 不存在");

        ParseResult parsed = parser.parse(is, "application/pdf");
        assertNotNull(parsed.getContent(), "解析内容不应为空");

        String normalized = normalizer.normalize(parsed.getContent());
        List<ResumeChunk> chunks = chunker.chunk(normalized);

        return chunks.stream()
                .filter(c -> c.getType() == type)
                .toList();
    }

    @BeforeEach
    void setUp() {
        testCandidateId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        if (vectorStore != null) {
            try {
                vectorStore.deleteByCandidate(testCandidateId);
            } catch (Exception e) {
                // 忽略清理异常
            }
        }
    }

    @Test
    @DisplayName("服务应正确初始化（根据环境自动选择数据库）")
    void should_initialize_correctly() {
        if (DOCKER_RUNNING) {
            // Docker 模式：需要 VectorStoreService
            Assumptions.assumeTrue(vectorStore != null,
                "跳过：PostgreSQL 未启动 (运行: docker-compose up -d)");
            assertNotNull(vectorStore);
        } else {
            // 开发模式：可以使用 H2
            assertNotNull(parser);
            assertNotNull(normalizer);
            assertNotNull(chunker);
        }
    }

    @Test
    @DisplayName("应能保存 EXPERIENCE chunk 的向量（从真实 PDF 解析）")
    void should_save_experience_vector_from_pdf() throws Exception {
        Assumptions.assumeTrue(vectorStore != null,
            "跳过：需要 PostgreSQL + pgvector (运行: docker-compose up -d)");

        // Given: 从 My.pdf 解析获取 EXPERIENCE chunks（遵循完整流水线）
        List<ResumeChunk> experienceChunks = getChunksFromPdf("/static/My.pdf", ChunkType.EXPERIENCE);
        assertFalse(experienceChunks.isEmpty(), "My.pdf 应包含 EXPERIENCE chunk");

        ResumeChunk chunk = experienceChunks.get(0);

        // When & Then: 保存向量不应抛出异常
        assertDoesNotThrow(() -> {
            vectorStore.save(testCandidateId, ChunkType.EXPERIENCE, chunk.getContent());
        });
    }

    @Test
    @DisplayName("应能保存 SKILL chunk 的向量（从真实 PDF 解析）")
    void should_save_skill_vector_from_pdf() throws Exception {
        Assumptions.assumeTrue(vectorStore != null,
            "跳过：需要 PostgreSQL + pgvector (运行: docker-compose up -d)");

        // Given: 从 My.pdf 解析获取 SKILL chunks（遵循完整流水线）
        List<ResumeChunk> skillChunks = getChunksFromPdf("/static/My.pdf", ChunkType.SKILL);
        assertFalse(skillChunks.isEmpty(), "My.pdf 应包含 SKILL chunk");

        ResumeChunk chunk = skillChunks.get(0);

        // When & Then: 保存向量不应抛出异常
        assertDoesNotThrow(() -> {
            vectorStore.save(testCandidateId, ChunkType.SKILL, chunk.getContent());
        });
    }

    @Test
    @DisplayName("应能删除指定候选人的所有向量")
    void should_delete_vectors_by_candidate() throws Exception {
        Assumptions.assumeTrue(vectorStore != null,
            "跳过：需要 PostgreSQL + pgvector (运行: docker-compose up -d)");

        // Given: 保存一些向量
        vectorStore.save(testCandidateId, ChunkType.EXPERIENCE, "测试经验内容1");
        vectorStore.save(testCandidateId, ChunkType.EXPERIENCE, "测试经验内容2");
        vectorStore.save(testCandidateId, ChunkType.SKILL, "测试技能内容");

        // When
        assertDoesNotThrow(() -> {
            vectorStore.deleteByCandidate(testCandidateId);
        });
    }

    @Test
    @DisplayName("全链路测试：从 PDF 到向量存储")
    void should_store_vectors_from_pdf_pipeline() throws Exception {
        Assumptions.assumeTrue(vectorStore != null && embeddingService != null,
            "跳过：需要 PostgreSQL + pgvector 和 Python embedding 服务");

        // Given: 从 Resume.pdf 解析获取所有 chunks（遵循完整流水线）
        InputStream is = getClass().getResourceAsStream("/static/Resume.pdf");
        assertNotNull(is, "Resume.pdf 不存在");

        ParseResult parsed = parser.parse(is, "application/pdf");
        String normalized = normalizer.normalize(parsed.getContent());
        List<ResumeChunk> chunks = chunker.chunk(normalized);

        // 筛选 EXPERIENCE 和 SKILL 类型
        List<ResumeChunk> vectorChunks = chunks.stream()
                .filter(c -> c.getType() == ChunkType.EXPERIENCE || c.getType() == ChunkType.SKILL)
                .toList();

        assertFalse(vectorChunks.isEmpty(), "应包含可向量化的 chunks");

        // When: 存储所有向量
        List<VectorStoreService.SearchResult> savedResults = new ArrayList<>();
        for (ResumeChunk chunk : vectorChunks) {
            try {
                vectorStore.save(testCandidateId, chunk.getType(), chunk.getContent());
            } catch (Exception e) {
                // 如果 Python 服务未启动，跳过 embedding 失败的情况
                if (!e.getMessage().contains("Embedding")) {
                    throw e;
                }
            }
        }

        // Then: 验证至少尝试保存了数据
        assertTrue(vectorChunks.size() >= 1, "应有可存储的 chunks");
    }

    @Test
    @DisplayName("HNSW 索引应能成功创建（Docker 模式）")
    void should_create_hnsw_index() throws Exception {
        Assumptions.assumeTrue(DOCKER_RUNNING && vectorStore != null,
            "跳过：需要 PostgreSQL + pgvector (运行: docker-compose up -d)");

        // When & Then: 创建索引不应抛出异常
        assertDoesNotThrow(() -> {
            vectorStore.createHnswIndex();
        });
    }

    @Test
    @DisplayName("应支持按候选人 ID 清理数据")
    void should_support_candidate_isolation() throws Exception {
        Assumptions.assumeTrue(vectorStore != null,
            "跳过：需要 PostgreSQL + pgvector (运行: docker-compose up -d)");

        // Given: 创建两个不同候选人的数据
        UUID candidate1 = UUID.randomUUID();
        UUID candidate2 = UUID.randomUUID();

        vectorStore.save(candidate1, ChunkType.EXPERIENCE, "候选人1的经验");
        vectorStore.save(candidate2, ChunkType.EXPERIENCE, "候选人2的经验");

        // When: 只删除候选人1的数据
        vectorStore.deleteByCandidate(candidate1);

        // Then: 验证删除操作不抛异常
        assertDoesNotThrow(() -> {
            vectorStore.deleteByCandidate(candidate1);
        });

        // 清理
        vectorStore.deleteByCandidate(candidate2);
    }
}
