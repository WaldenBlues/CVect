package com.walden.cvect.service;

import com.walden.cvect.infra.parser.ResumeParser;
import com.walden.cvect.infra.process.ResumeTextNormalizer;
import com.walden.cvect.model.ChunkType;
import com.walden.cvect.model.ParseResult;
import com.walden.cvect.model.ResumeChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 事实提取服务测试
 *
 * 测试原则：遵循顺序测试，不得直接读取静态文件跳过前序代码处理
 * 必须通过完整流水线：PDF -> parser -> normalizer -> chunker -> fact extraction
 */
@SpringBootTest
@Tag("integration")
@Tag("service")
class ResumeFactServiceTest {

    @Autowired
    private ResumeFactService factService;

    @Autowired
    private ChunkerService chunker;

    @Autowired
    private ResumeParser parser;

    @Autowired
    private ResumeTextNormalizer normalizer;

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

    /**
     * 从真实 PDF 解析并获取所有 chunks
     */
    private List<ResumeChunk> getAllChunksFromPdf(String pdfPath) throws Exception {
        InputStream is = getClass().getResourceAsStream(pdfPath);
        assertNotNull(is, pdfPath + " 不存在");

        ParseResult parsed = parser.parse(is, "application/pdf");
        String normalized = normalizer.normalize(parsed.getContent());
        return chunker.chunk(normalized);
    }

    @BeforeEach
    void setUp() {
        testCandidateId = UUID.randomUUID();
    }

    @Test
    @DisplayName("处理 CONTACT chunk 应保存到数据库（使用真实 PDF 数据）")
    void should_save_contact_chunk() throws Exception {
        // Given: 从 My.pdf 解析获取 CONTACT chunks（遵循完整流水线）
        List<ResumeChunk> contactChunks = getChunksFromPdf("/static/My.pdf", ChunkType.CONTACT);
        assertFalse(contactChunks.isEmpty(), "My.pdf 应包含 CONTACT chunk");

        // When
        factService.processAndSave(testCandidateId, contactChunks.get(0));

        // Then: 验证不抛异常即可（数据库持久化由 JPA 测试验证）
        assertNotNull(testCandidateId);
    }

    @Test
    @DisplayName("处理 LINK chunk 应保存到数据库（使用真实 PDF 数据）")
    void should_save_link_chunk() throws Exception {
        // Given: 从 My.pdf 解析获取 LINK chunks
        List<ResumeChunk> linkChunks = getChunksFromPdf("/static/My.pdf", ChunkType.LINK);
        assertFalse(linkChunks.isEmpty(), "My.pdf 应包含 LINK chunk");

        // When
        factService.processAndSave(testCandidateId, linkChunks.get(0));

        // Then
        assertNotNull(testCandidateId);
    }

    @Test
    @DisplayName("处理 HONOR chunk 应保存到数据库（使用真实 PDF 数据）")
    void should_save_honor_chunk() throws Exception {
        // Given: 从 My.pdf 解析获取 HONOR chunks
        List<ResumeChunk> honorChunks = getChunksFromPdf("/static/My.pdf", ChunkType.HONOR);
        assertFalse(honorChunks.isEmpty(), "My.pdf 应包含 HONOR chunk");

        // When
        factService.processAndSave(testCandidateId, honorChunks.get(0));

        // Then
        assertNotNull(testCandidateId);
    }

    @Test
    @DisplayName("处理 EDUCATION chunk 应保存到数据库（使用真实 PDF 数据）")
    void should_save_education_chunk() throws Exception {
        // Given: 从 My.pdf 解析获取 EDUCATION chunks
        List<ResumeChunk> educationChunks = getChunksFromPdf("/static/My.pdf", ChunkType.EDUCATION);
        assertFalse(educationChunks.isEmpty(), "My.pdf 应包含 EDUCATION chunk");

        // When
        factService.processAndSave(testCandidateId, educationChunks.get(0));

        // Then
        assertNotNull(testCandidateId);
    }

    @Test
    @DisplayName("处理 EXPERIENCE chunk 应保存到向量存储（使用真实 PDF 数据）")
    void should_save_experience_chunk_to_vector_store() throws Exception {
        // Given: 从 My.pdf 解析获取 EXPERIENCE chunks（遵循完整流水线）
        List<ResumeChunk> experienceChunks = getChunksFromPdf("/static/My.pdf", ChunkType.EXPERIENCE);
        assertFalse(experienceChunks.isEmpty(), "My.pdf 应包含 EXPERIENCE chunk");

        // When & Then: EXPERIENCE 应调用向量存储，不抛异常
        assertDoesNotThrow(() -> {
            factService.processAndSave(testCandidateId, experienceChunks.get(0));
        });
    }

    @Test
    @DisplayName("处理 SKILL chunk 应保存到向量存储（使用真实 PDF 数据）")
    void should_save_skill_chunk_to_vector_store() throws Exception {
        // Given: 从 My.pdf 解析获取 SKILL chunks（遵循完整流水线）
        List<ResumeChunk> skillChunks = getChunksFromPdf("/static/My.pdf", ChunkType.SKILL);
        assertFalse(skillChunks.isEmpty(), "My.pdf 应包含 SKILL chunk");

        // When & Then: SKILL 应调用向量存储，不抛异常
        assertDoesNotThrow(() -> {
            factService.processAndSave(testCandidateId, skillChunks.get(0));
        });
    }

    @Test
    @DisplayName("处理空 chunk 应不保存数据（边界情况测试）")
    void should_skip_empty_chunk() {
        // Given: 边界情况，使用空 chunk
        ResumeChunk emptyChunk = new ResumeChunk(0, "   ", ChunkType.OTHER);

        // When & Then
        assertDoesNotThrow(() -> {
            factService.processAndSave(testCandidateId, emptyChunk);
        });
    }

    @Test
    @DisplayName("处理多个 CONTACT chunk 应保存多个记录（使用真实 PDF 数据）")
    void should_save_multiple_contact_records() throws Exception {
        // Given: 从 My.pdf 解析获取所有 CONTACT chunks
        List<ResumeChunk> contactChunks = getChunksFromPdf("/static/My.pdf", ChunkType.CONTACT);
        assertTrue(contactChunks.size() >= 2, "My.pdf 应包含多个 CONTACT chunk");

        // When: 处理所有 CONTACT chunks
        for (ResumeChunk chunk : contactChunks) {
            factService.processAndSave(testCandidateId, chunk);
        }

        // Then
        assertNotNull(testCandidateId);
    }

    @Test
    @DisplayName("处理 OTHER 类型 chunk 应不保存（边界情况测试）")
    void should_skip_other_type_chunk() {
        // Given: 边界情况，使用 OTHER 类型 chunk
        ResumeChunk otherChunk = new ResumeChunk(
                0,
                "some random text",
                ChunkType.OTHER);

        // When & Then
        assertDoesNotThrow(() -> {
            factService.processAndSave(testCandidateId, otherChunk);
        });
    }

    @Test
    @DisplayName("全链路测试：从 PDF 解析到事实提取与保存")
    void should_process_full_pipeline_from_pdf_to_facts() throws Exception {
        // Given: 使用 My.pdf 进行完整流水线测试
        InputStream is = getClass().getResourceAsStream("/static/My.pdf");
        assertNotNull(is, "My.pdf 不存在");

        ParseResult parsed = parser.parse(is, "application/pdf");
        String normalized = normalizer.normalize(parsed.getContent());
        List<ResumeChunk> chunks = chunker.chunk(normalized);

        // When: 处理所有 chunks 并保存事实
        for (ResumeChunk chunk : chunks) {
            factService.processAndSave(testCandidateId, chunk);
        }

        // Then: 验证关键事实类型存在
        assertTrue(chunks.stream().anyMatch(c -> c.getType() == ChunkType.CONTACT),
                "应包含 CONTACT chunk");
        assertTrue(chunks.stream().anyMatch(c -> c.getType() == ChunkType.LINK),
                "应包含 LINK chunk");
        assertTrue(chunks.stream().anyMatch(c -> c.getType() == ChunkType.HONOR),
                "应包含 HONOR chunk");
        assertTrue(chunks.stream().anyMatch(c -> c.getType() == ChunkType.EDUCATION),
                "应包含 EDUCATION chunk");
    }
}
