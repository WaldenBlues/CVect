package com.walden.cvect.model.entity.vector;

import com.walden.cvect.model.ChunkType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ResumeChunkVector 实体测试
 *
 * 测试原则：遵循完整流水线测试模式
 * PDF -> parser -> normalizer -> chunker -> fact extraction -> vector storage
 */
@DisplayName("ResumeChunkVector 实体测试")
class ResumeChunkVectorTest {

    private final UUID testCandidateId = UUID.randomUUID();
    private final float[] testEmbedding = new float[768];

    @Test
    @DisplayName("实体应正确初始化所有字段")
    void should_initialize_all_fields() {
        // Given
        String content = "负责后端开发，使用 Java 和 Spring Boot";

        // When
        ResumeChunkVector vector = new ResumeChunkVector(
                testCandidateId,
                ChunkType.EXPERIENCE,
                content,
                testEmbedding
        );

        // Then
        assertEquals(testCandidateId, vector.getCandidateId());
        assertEquals(ChunkType.EXPERIENCE, vector.getChunkType());
        assertEquals(content, vector.getContent());
        assertArrayEquals(testEmbedding, vector.getEmbedding());
        assertNull(vector.getId());
        assertNull(vector.getCreatedAt());
    }

    @Test
    @DisplayName("实体应支持 SKILL 类型")
    void should_support_skill_type() {
        // Given
        String content = "熟练使用 Java, Python, Docker";

        // When
        ResumeChunkVector vector = new ResumeChunkVector(
                testCandidateId,
                ChunkType.SKILL,
                content,
                testEmbedding
        );

        // Then
        assertEquals(ChunkType.SKILL, vector.getChunkType());
    }

    @Test
    @DisplayName("实体应支持不同时期的创建时间")
    void should_handle_created_at_timing() {
        // Given
        ResumeChunkVector vector = new ResumeChunkVector(
                testCandidateId,
                ChunkType.EXPERIENCE,
                "测试内容",
                testEmbedding
        );

        // When: 模拟 PrePersist 行为
        vector.onCreate();

        // Then
        assertNotNull(vector.getCreatedAt());
        assertTrue(vector.getCreatedAt().isBefore(LocalDateTime.now().plusSeconds(1)));
    }

    @Test
    @DisplayName("实体应支持不同维度 embedding")
    void should_support_different_embedding_dimensions() {
        // Given
        float[] smallEmbedding = new float[128];
        float[] largeEmbedding = new float[1024];

        // When
        ResumeChunkVector small = new ResumeChunkVector(
                testCandidateId, ChunkType.EXPERIENCE, "内容1", smallEmbedding);
        ResumeChunkVector large = new ResumeChunkVector(
                testCandidateId, ChunkType.SKILL, "内容2", largeEmbedding);

        // Then
        assertEquals(128, small.getEmbedding().length);
        assertEquals(1024, large.getEmbedding().length);
    }

    @Test
    @DisplayName("实体 getter 方法应正常工作")
    void should_return_correct_values() {
        // Given
        String content = "阿里巴巴 - 高级工程师";
        ResumeChunkVector vector = new ResumeChunkVector(
                testCandidateId,
                ChunkType.EXPERIENCE,
                content,
                testEmbedding
        );

        // Then: 验证所有 getter 方法（ID 在持久化前为 null）
        assertNull(vector.getId(), "持久化前 ID 应为 null");
        assertEquals(testCandidateId, vector.getCandidateId());
        assertEquals(ChunkType.EXPERIENCE, vector.getChunkType());
        assertEquals(content, vector.getContent());
        assertArrayEquals(testEmbedding, vector.getEmbedding());
    }
}
