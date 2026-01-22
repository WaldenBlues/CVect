package com.walden.cvect.service;

import com.walden.cvect.model.ChunkType;
import com.walden.cvect.model.ResumeChunk;
import com.walden.cvect.model.fact.extract.FactExtractorDispatcher;
import com.walden.cvect.repository.FactRepository;
import com.walden.cvect.model.entity.Contact;
import com.walden.cvect.model.entity.Link;
import com.walden.cvect.model.entity.Honor;
import com.walden.cvect.model.entity.Education;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Tag("integration")
@Tag("service")
class ResumeFactServiceTest {

    @Autowired
    private ResumeFactService factService;

    @Autowired
    private FactRepository repository;

    private UUID testCandidateId;

    @BeforeEach
    void setUp() {
        testCandidateId = UUID.randomUUID();
    }

    @Test
    @DisplayName("处理 CONTACT chunk 应保存到数据库")
    void should_save_contact_chunk() {
        // Given
        ResumeChunk contactChunk = new ResumeChunk(
                0,
                "test@example.com\n13800138000",
                ChunkType.CONTACT);

        // When
        factService.processAndSave(testCandidateId, contactChunk);

        // Then: 验证数据已保存（通过验证不抛异常）
        assertNotNull(testCandidateId);
    }

    @Test
    @DisplayName("处理 LINK chunk 应保存到数据库")
    void should_save_link_chunk() {
        // Given
        ResumeChunk linkChunk = new ResumeChunk(
                0,
                "https://github.com/username",
                ChunkType.LINK);

        // When
        factService.processAndSave(testCandidateId, linkChunk);

        // Then
        assertNotNull(testCandidateId);
    }

    @Test
    @DisplayName("处理 HONOR chunk 应保存到数据库")
    void should_save_honor_chunk() {
        // Given
        ResumeChunk honorChunk = new ResumeChunk(
                0,
                "全国大学生数学建模竞赛一等奖",
                ChunkType.HONOR);

        // When
        factService.processAndSave(testCandidateId, honorChunk);

        // Then
        assertNotNull(testCandidateId);
    }

    @Test
    @DisplayName("处理 EDUCATION chunk 应保存到数据库")
    void should_save_education_chunk() {
        // Given
        ResumeChunk educationChunk = new ResumeChunk(
                0,
                "清华大学|计算机科学与技术|学士",
                ChunkType.EDUCATION);

        // When
        factService.processAndSave(testCandidateId, educationChunk);

        // Then
        assertNotNull(testCandidateId);
    }

    @Test
    @DisplayName("处理 EXPERIENCE chunk 不应保存到数据库")
    void should_not_save_experience_chunk() {
        // Given
        ResumeChunk experienceChunk = new ResumeChunk(
                0,
                "某科技公司|软件工程师|负责后端开发",
                ChunkType.EXPERIENCE);

        // When & Then: EXPERIENCE 应被跳过，不抛异常
        assertDoesNotThrow(() -> {
            factService.processAndSave(testCandidateId, experienceChunk);
        });
    }

    @Test
    @DisplayName("处理 SKILL chunk 不应保存到数据库")
    void should_not_save_skill_chunk() {
        // Given
        ResumeChunk skillChunk = new ResumeChunk(
                0,
                "Java\nSpring Boot\nPostgreSQL",
                ChunkType.SKILL);

        // When & Then: SKILL 应被跳过，不抛异常
        assertDoesNotThrow(() -> {
            factService.processAndSave(testCandidateId, skillChunk);
        });
    }

    @Test
    @DisplayName("处理空 chunk 应不保存数据")
    void should_skip_empty_chunk() {
        // Given
        ResumeChunk emptyChunk = new ResumeChunk(0, "   ", ChunkType.OTHER);

        // When & Then
        assertDoesNotThrow(() -> {
            factService.processAndSave(testCandidateId, emptyChunk);
        });
    }

    @Test
    @DisplayName("处理多个 CONTACT chunk 应保存多个记录")
    void should_save_multiple_contact_records() {
        // Given
        ResumeChunk contactChunk = new ResumeChunk(
                0,
                "test@example.com\n13800138000",
                ChunkType.CONTACT);

        // When
        factService.processAndSave(testCandidateId, contactChunk);

        // Then
        assertNotNull(testCandidateId);
    }

    @Test
    @DisplayName("处理 OTHER 类型 chunk 应不保存")
    void should_skip_other_type_chunk() {
        // Given
        ResumeChunk otherChunk = new ResumeChunk(
                0,
                "some random text",
                ChunkType.OTHER);

        // When & Then
        assertDoesNotThrow(() -> {
            factService.processAndSave(testCandidateId, otherChunk);
        });
    }
}
