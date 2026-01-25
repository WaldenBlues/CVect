package com.walden.cvect.service;

import com.walden.cvect.model.ChunkType;
import com.walden.cvect.model.ResumeChunk;
import com.walden.cvect.infra.parser.ResumeParser;
import com.walden.cvect.infra.process.ResumeTextNormalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.InputStream;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Tag("integration")
@Tag("service")
class ResumeProcessServiceTest {

    @Autowired
    private ResumeProcessService processService;

    @Autowired
    private ResumeParser parser;

    @Autowired
    private ResumeTextNormalizer normalizer;

    @Test
    @DisplayName("处理 My.pdf 应返回 chunks 和 candidateId")
    void should_process_my_pdf_and_return_result() throws Exception {
        // Given
        InputStream is = getClass().getResourceAsStream("/static/My.pdf");
        assertNotNull(is, "My.pdf 文件不存在");

        // When
        var result = processService.process(is, "application/pdf");

        // Then
        assertNotNull(result, "ProcessResult 不应为 null");
        assertNotNull(result.candidateId(), "candidateId 不应为 null");
        assertNotNull(result.chunks(), "chunks 不应为 null");
        assertFalse(result.chunks().isEmpty(), "chunks 不应为空");

        // 验证 chunks 内容
        for (ResumeChunk chunk : result.chunks()) {
            assertNotNull(chunk.getContent(), "chunk 内容不应为空");
            assertNotNull(chunk.getType(), "chunk 类型不应为空");
            assertTrue(chunk.getIndex() >= 0, "chunk index 应为非负数");
        }

        // 应包含非 OTHER 类型的 chunk
        boolean hasSemanticChunk = result.chunks().stream()
                .anyMatch(c -> c.getType() != ChunkType.OTHER);
        assertTrue(hasSemanticChunk, "应包含语义类型的 chunk");
    }

    @Test
    @DisplayName("处理 Resume.pdf 应返回不同的 chunks")
    void should_process_resume_pdf_and_return_chunks() throws Exception {
        // Given
        InputStream is = getClass().getResourceAsStream("/static/Resume.pdf");
        assertNotNull(is, "Resume.pdf 文件不存在");

        // When
        var result = processService.process(is, "application/pdf");

        // Then
        assertNotNull(result.chunks());
        assertFalse(result.chunks().isEmpty());

        // 验证不同类型的 chunk 存在
        List<ChunkType> types = result.chunks().stream()
                .map(ResumeChunk::getType)
                .distinct()
                .toList();

        // 至少应有 HEADER, CONTACT 等类型
        assertTrue(types.size() >= 2, "至少应有 2 种不同类型的 chunk");
    }

    @Test
    @DisplayName("每次处理应生成唯一的 candidateId")
    void should_generate_unique_candidate_id_for_each_process() throws Exception {
        // Given
        InputStream is1 = getClass().getResourceAsStream("/static/My.pdf");
        InputStream is2 = getClass().getResourceAsStream("/static/Resume.pdf");

        // When
        var result1 = processService.process(is1, "application/pdf");
        var result2 = processService.process(is2, "application/pdf");

        // Then
        assertNotNull(result1.candidateId());
        assertNotNull(result2.candidateId());
        assertNotEquals(result1.candidateId(), result2.candidateId(),
                "每次处理应生成不同的 candidateId");
    }

    @Test
    @DisplayName("处理后的 chunks 应包含 CONTACT 类型")
    void should_include_contact_chunks() throws Exception {
        // Given
        InputStream is = getClass().getResourceAsStream("/static/My.pdf");

        // When
        var result = processService.process(is, "application/pdf");

        // Then
        boolean hasContact = result.chunks().stream()
                .anyMatch(c -> c.getType() == ChunkType.CONTACT);

        assertTrue(hasContact, "应包含 CONTACT 类型的 chunk");
    }

    @Test
    @DisplayName("处理后的 chunks 应包含 LINK 类型")
    void should_include_link_chunks() throws Exception {
        // Given
        InputStream is = getClass().getResourceAsStream("/static/Resume.pdf");

        // When
        var result = processService.process(is, "application/pdf");

        // Then
        boolean hasLink = result.chunks().stream()
                .anyMatch(c -> c.getType() == ChunkType.LINK);

        assertTrue(hasLink, "应包含 LINK 类型的 chunk");
    }
}
