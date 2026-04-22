package com.walden.cvect.infra.vector;

import com.walden.cvect.config.TestEmbeddings;
import com.walden.cvect.infra.embedding.EmbeddingService;
import com.walden.cvect.model.ChunkType;
import com.walden.cvect.model.ParseResult;
import com.walden.cvect.model.ResumeChunk;
import com.walden.cvect.infra.parser.ResumeParser;
import com.walden.cvect.infra.process.ResumeTextNormalizer;
import com.walden.cvect.model.entity.Candidate;
import com.walden.cvect.repository.CandidateJpaRepository;
import com.walden.cvect.service.resume.ChunkerService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.walden.cvect.config.PostgresIntegrationTestBase;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * VectorStoreService 集成测试
 *
 * 测试原则：遵循完整流水线测试模式
 * PDF -> parser -> normalizer -> chunker -> fact extraction -> vector storage
 *
 * 前置条件：Testcontainers 提供 PostgreSQL + pgvector
 * embedding 依赖由 mock 提供，避免依赖外部 HTTP 服务
 *
 * 如果 Docker 不可用，测试会自动跳过
 */
@SpringBootTest(properties = {
        "app.upload.worker.enabled=false",
        "app.vector.ingest.worker.enabled=false"
})
@Tag("integration")
@Tag("vector")
@DisplayName("VectorStoreService 集成测试（流水线测试）")
class VectorStoreServiceIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired(required = false)
    private VectorStoreService vectorStore;

    @Autowired
    private ResumeParser parser;

    @Autowired
    private ResumeTextNormalizer normalizer;

    @Autowired
    private ChunkerService chunker;

    @MockBean
    private EmbeddingService embeddingService;

    @Autowired
    private CandidateJpaRepository candidateRepository;

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

    private boolean isVectorStoreReady() {
        return vectorStore != null && vectorStore.isOperational();
    }

    @BeforeEach
    void setUp() {
        testCandidateId = createCandidateId("vector-integration");
        when(embeddingService.embed(anyString()))
                .thenAnswer(invocation ->
                        TestEmbeddings.forText(invocation.getArgument(0, String.class)));
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
    @DisplayName("服务应正确初始化")
    void should_initialize_correctly() {
        Assumptions.assumeTrue(isVectorStoreReady(),
            "跳过：需要 PostgreSQL + pgvector");

        assertNotNull(vectorStore);
        assertNotNull(parser);
        assertNotNull(normalizer);
        assertNotNull(chunker);
    }

    @Test
    @DisplayName("应能保存 EXPERIENCE chunk 的向量（从真实 PDF 解析）")
    void should_save_experience_vector_from_pdf() throws Exception {
        Assumptions.assumeTrue(isVectorStoreReady(),
            "跳过：需要 PostgreSQL + pgvector");

        // Given: 从 My.pdf 解析获取 EXPERIENCE chunks（遵循完整流水线）
        List<ResumeChunk> experienceChunks = getChunksFromPdf("/static/My.pdf", ChunkType.EXPERIENCE);
        assertFalse(experienceChunks.isEmpty(), "My.pdf 应包含 EXPERIENCE chunk");

        ResumeChunk chunk = experienceChunks.get(0);

        // When & Then: 保存向量应成功持久化
        assertTrue(vectorStore.save(testCandidateId, ChunkType.EXPERIENCE, chunk.getContent()));
    }

    @Test
    @DisplayName("应能保存 SKILL chunk 的向量（从真实 PDF 解析）")
    void should_save_skill_vector_from_pdf() throws Exception {
        Assumptions.assumeTrue(isVectorStoreReady(),
            "跳过：需要 PostgreSQL + pgvector (运行: docker-compose up -d)");

        // Given: 从 My.pdf 解析获取 SKILL chunks（遵循完整流水线）
        List<ResumeChunk> skillChunks = getChunksFromPdf("/static/My.pdf", ChunkType.SKILL);
        assertFalse(skillChunks.isEmpty(), "My.pdf 应包含 SKILL chunk");

        ResumeChunk chunk = skillChunks.get(0);

        // When & Then: 保存向量应成功持久化
        assertTrue(vectorStore.save(testCandidateId, ChunkType.SKILL, chunk.getContent()));
    }

    @Test
    @DisplayName("应能删除指定候选人的所有向量")
    void should_delete_vectors_by_candidate() throws Exception {
        Assumptions.assumeTrue(isVectorStoreReady(),
            "跳过：需要 PostgreSQL + pgvector (运行: docker-compose up -d)");

        // Given: 保存一些向量
        assertTrue(vectorStore.save(testCandidateId, ChunkType.EXPERIENCE, "测试经验内容1"));
        assertTrue(vectorStore.save(testCandidateId, ChunkType.EXPERIENCE, "测试经验内容2"));
        assertTrue(vectorStore.save(testCandidateId, ChunkType.SKILL, "测试技能内容"));

        // When
        assertDoesNotThrow(() -> {
            vectorStore.deleteByCandidate(testCandidateId);
        });
    }

    @Test
    @DisplayName("全链路测试：从 PDF 到向量存储")
    void should_store_vectors_from_pdf_pipeline() throws Exception {
        Assumptions.assumeTrue(isVectorStoreReady(),
            "跳过：需要 PostgreSQL + pgvector");

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
        for (ResumeChunk chunk : vectorChunks) {
            assertTrue(vectorStore.save(testCandidateId, chunk.getType(), chunk.getContent()),
                    "应成功保存 chunk: " + chunk.getType());
        }

        // Then: 验证至少有一个可存储 chunk，且都已成功保存
        assertTrue(vectorChunks.size() >= 1, "应有可存储的 chunks");
    }

    @Test
    @DisplayName("向量索引应能成功创建（Docker 模式）")
    void should_create_vector_index() throws Exception {
        Assumptions.assumeTrue(isVectorStoreReady(),
            "跳过：需要 PostgreSQL + pgvector");

        try {
            vectorStore.createVectorIndex();
        } catch (Exception e) {
            String message = e.getMessage() == null ? "" : e.getMessage();
            Assumptions.assumeTrue(
                    !message.toLowerCase().contains("hnsw"),
                    "跳过：当前数据库不支持 HNSW 索引");
            throw e;
        }
    }

    @Test
    @DisplayName("应支持按候选人 ID 清理数据")
    void should_support_candidate_isolation() throws Exception {
        Assumptions.assumeTrue(isVectorStoreReady(),
            "跳过：需要 PostgreSQL + pgvector (运行: docker-compose up -d)");

        // Given: 创建两个不同候选人的数据
        UUID candidate1 = createCandidateId("vector-c1");
        UUID candidate2 = createCandidateId("vector-c2");

        assertTrue(vectorStore.save(candidate1, ChunkType.EXPERIENCE, "候选人1的经验"));
        assertTrue(vectorStore.save(candidate2, ChunkType.EXPERIENCE, "候选人2的经验"));

        // When: 只删除候选人1的数据
        vectorStore.deleteByCandidate(candidate1);

        // Then: 验证删除操作不抛异常
        assertDoesNotThrow(() -> {
            vectorStore.deleteByCandidate(candidate1);
        });

        // 清理
        vectorStore.deleteByCandidate(candidate2);
    }

    private UUID createCandidateId(String namePrefix) {
        Candidate candidate = new Candidate(
                namePrefix + ".pdf",
                UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""),
                namePrefix,
                null,
                "application/pdf",
                128L,
                128,
                false);
        return candidateRepository.save(candidate).getId();
    }
}
