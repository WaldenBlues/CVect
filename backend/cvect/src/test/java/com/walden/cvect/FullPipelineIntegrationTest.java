package com.walden.cvect;

import com.walden.cvect.service.resume.ResumeProcessService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Tag("integration")
@Tag("pipeline")
class FullPipelineIntegrationTest {

    @Autowired
    private ResumeProcessService processService;

    @Test
    @DisplayName("完整流水线：My.pdf → 解析 → 分块 → 存储 → 返回")
    void should_process_full_pipeline_with_my_pdf() throws Exception {
        // Given
        InputStream is = getClass().getResourceAsStream("/static/My.pdf");
        assertNotNull(is, "My.pdf 文件不存在");

        // When: 执行完整流水线
        var result = processService.process(
                is,
                "application/pdf",
                "My.pdf",
                null,
                null);

        // Then: 验证返回结果
        assertNotNull(result, "ProcessResult 不应为 null");
        assertNotNull(result.candidateId(), "candidateId 不应为 null");
        assertNotNull(result.chunks(), "chunks 不应为 null");
        assertFalse(result.chunks().isEmpty(), "chunks 不应为空");

        // 验证 candidateId 格式
        UUID.fromString(result.candidateId().toString());

        // 验证 chunks 包含多种类型
        var types = result.chunks().stream()
                .map(chunk -> chunk.getType())
                .distinct()
                .toList();

        assertTrue(types.size() >= 2, "至少应有 2 种不同类型的 chunk");

        // 打印结果用于调试
        System.out.println("========== FULL PIPELINE RESULT ==========");
        System.out.println("candidateId: " + result.candidateId());
        System.out.println("total chunks: " + result.chunks().size());
        System.out.println("chunk types: " + types);
        System.out.println("==========================================");
    }

    @Test
    @DisplayName("完整流水线：Resume.pdf → 解析 → 分块 → 存储 → 返回")
    void should_process_full_pipeline_with_resume_pdf() throws Exception {
        // Given
        InputStream is = getClass().getResourceAsStream("/static/Resume.pdf");
        assertNotNull(is, "Resume.pdf 文件不存在");

        // When
        var result = processService.process(
                is,
                "application/pdf",
                "Resume.pdf",
                null,
                null);

        // Then
        assertNotNull(result.candidateId());
        assertNotNull(result.chunks());
        assertFalse(result.chunks().isEmpty());

        System.out.println("========== RESUME.PDF RESULT ==========");
        System.out.println("candidateId: " + result.candidateId());
        System.out.println("total chunks: " + result.chunks().size());
        System.out.println("========================================");
    }

    @Test
    @DisplayName("两次处理同一文件应命中去重并返回同一 candidateId")
    void should_return_same_id_for_same_file_when_deduplicated() throws Exception {
        // Given: 构造本用例唯一内容，避免受其他测试已入库数据影响
        String uniqueText = "Dedup test resume "
                + UUID.randomUUID()
                + "\nJava Spring Boot"
                + "\nSkills: Docker, PostgreSQL";
        byte[] payload = uniqueText.getBytes(StandardCharsets.UTF_8);
        InputStream is1 = new ByteArrayInputStream(payload);
        InputStream is2 = new ByteArrayInputStream(payload);

        // When
        var result1 = processService.process(
                is1,
                "text/plain",
                "dedup-test.txt",
                (long) payload.length,
                null);
        var result2 = processService.process(
                is2,
                "text/plain",
                "dedup-test.txt",
                (long) payload.length,
                null);

        // Then
        assertEquals(result1.candidateId(), result2.candidateId(),
                "相同文件应命中去重并返回同一 candidateId");
        assertFalse(result1.duplicated(), "首次处理不应标记为重复");
        assertTrue(result2.duplicated(), "二次处理应标记为重复");

        // chunks 数量应该相同
        assertEquals(result1.chunks().size(), result2.chunks().size(),
                "同一文件应生成相同数量的 chunks");
    }
}
