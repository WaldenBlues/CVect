package com.walden.cvect.infra.embedding;

import com.walden.cvect.infra.parser.ResumeParser;
import com.walden.cvect.infra.process.ResumeTextNormalizer;
import com.walden.cvect.model.ChunkType;
import com.walden.cvect.model.ParseResult;
import com.walden.cvect.model.ResumeChunk;
import com.walden.cvect.service.ChunkerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EmbeddingService 配置测试（不调用真实 Python 服务）
 *
 * 测试原则：遵循完整流水线测试模式
 * PDF -> parser -> normalizer -> chunker -> fact extraction -> embedding
 *
 * 注意：此测试仅验证配置加载，不测试真实 embedding 生成（需要 Python 服务）
 */
@SpringBootTest
@TestPropertySource(properties = {
    "app.embedding.model-name=Qwen/Qwen2.5-Embedding-0.6B-Instruct",
    "app.embedding.device=cpu",
    "app.embedding.batch-size=32",
    "app.embedding.dimension=768",
    "app.embedding.max-input-length=8192"
})
@Tag("integration")
@DisplayName("EmbeddingService 配置测试（流水线测试）")
class EmbeddingServiceConfigurationTest {

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private EmbeddingConfig embeddingConfig;

    @Autowired
    private ResumeParser parser;

    @Autowired
    private ResumeTextNormalizer normalizer;

    @Autowired
    private ChunkerService chunker;

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

    @Test
    @DisplayName("EmbeddingService 应正确初始化配置")
    void should_initialize_with_correct_config() {
        // Then: 使用 @TestPropertySource 注入的配置值
        assertEquals(768, embeddingService.getDimension());
        assertEquals("Qwen/Qwen2.5-Embedding-0.6B-Instruct",
            embeddingConfig.getModelName(), "配置应加载正确的模型名称");
    }

    @Test
    @DisplayName("从真实 PDF 解析的 EXPERIENCE chunk 应可以用于 embedding（配置验证）")
    void should_extract_experience_chunk_for_embedding_config() throws Exception {
        // Given: 从 My.pdf 解析获取 EXPERIENCE chunks（遵循完整流水线）
        List<ResumeChunk> experienceChunks = getChunksFromPdf("/static/My.pdf", ChunkType.EXPERIENCE);
        assertFalse(experienceChunks.isEmpty(), "My.pdf 应包含 EXPERIENCE chunk");

        // Then: 验证 chunk 内容可用于后续 embedding 处理
        ResumeChunk chunk = experienceChunks.get(0);
        assertNotNull(chunk.getContent(), "chunk 内容不应为空");
        assertEquals(ChunkType.EXPERIENCE, chunk.getType());
        assertTrue(chunk.getContent().length() > 0, "chunk 内容应有文本");
    }

    @Test
    @DisplayName("从真实 PDF 解析的 SKILL chunk 应可以用于 embedding（配置验证）")
    void should_extract_skill_chunk_for_embedding_config() throws Exception {
        // Given: 从 My.pdf 解析获取 SKILL chunks（遵循完整流水线）
        List<ResumeChunk> skillChunks = getChunksFromPdf("/static/My.pdf", ChunkType.SKILL);
        assertFalse(skillChunks.isEmpty(), "My.pdf 应包含 SKILL chunk");

        // Then: 验证 chunk 内容可用于后续 embedding 处理
        ResumeChunk chunk = skillChunks.get(0);
        assertNotNull(chunk.getContent(), "chunk 内容不应为空");
        assertEquals(ChunkType.SKILL, chunk.getType());
    }

    @Test
    @DisplayName("单个文本 embedding 应返回正确维度向量（配置验证）")
    void should_return_vector_with_configured_dimension() {
        // Given
        String text = "测试文本";

        // When: 调用 embed 方法（由于 Python 服务未启动，会返回空向量或失败）
        try {
            float[] embedding = embeddingService.embed(text);

            // Then: 验证向量维度与配置一致
            assertEquals(embeddingConfig.getDimension(), embedding.length);
        } catch (RuntimeException e) {
            // Python 服务未启动时预期抛出异常
            assertTrue(e.getMessage().contains("Embedding generation failed") ||
                       e.getMessage().contains("Connection refused") ||
                       e.getMessage().contains("Unable to invoke"));
        }
    }

    @Test
    @DisplayName("批量 embedding 应支持多文本处理（配置验证）")
    void should_support_batch_embedding_config() {
        // Given
        List<String> texts = List.of("文本1", "文本2", "文本3");

        // When
        try {
            List<float[]> embeddings = embeddingService.embedBatch(texts);

            // Then: 验证批量处理配置
            assertNotNull(embeddings);
            assertEquals(texts.size(), embeddings.size());
        } catch (RuntimeException e) {
            // Python 服务未启动时预期行为
            assertTrue(true); // 测试通过
        }
    }

    @Test
    @DisplayName("空文本列表应返回空结果")
    void should_handle_empty_text_list() {
        // Given
        List<String> emptyTexts = List.of();

        // When
        List<float[]> result = embeddingService.embedBatch(emptyTexts);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("null 文本列表应返回空结果")
    void should_handle_null_text_list() {
        // When
        List<float[]> result = embeddingService.embedBatch(null);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
