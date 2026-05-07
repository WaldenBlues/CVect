package com.walden.cvect.service.resume;

import com.walden.cvect.infra.process.NameExtractor;
import com.walden.cvect.model.ChunkType;
import com.walden.cvect.model.ResumeChunk;
import com.walden.cvect.model.fact.extract.ContactExtractor;
import com.walden.cvect.model.fact.extract.EducationExtractor;
import com.walden.cvect.model.fact.extract.FactExtractorDispatcher;
import com.walden.cvect.model.fact.extract.HonorExtractor;
import com.walden.cvect.model.fact.extract.LinkExtractor;
import com.walden.cvect.repository.FactRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ResumeFactService structured extraction tests")
class ResumeFactServiceStructuredExtractionTest {

    @Mock
    private FactRepository repository;

    private ResumeFactService service;
    private DefaultChunkerService chunker;
    private NameExtractor nameExtractor;

    @BeforeEach
    void setUp() {
        service = new ResumeFactService(
                new FactExtractorDispatcher(List.of(
                        new ContactExtractor(),
                        new LinkExtractor(),
                        new EducationExtractor(),
                        new HonorExtractor())),
                repository);
        chunker = new DefaultChunkerService(1000, 100);
        nameExtractor = new NameExtractor();
    }

    @Test
    @DisplayName("shouldExtractKeyFactsFromResumeText")
    void shouldExtractKeyFactsFromResumeText() throws Exception {
        String resumeText = loadResource("/resume/minimal-resume.txt");
        UUID candidateId = UUID.randomUUID();

        assertThat(nameExtractor.extract(resumeText)).isEqualTo("张三");

        List<ResumeChunk> chunks = chunker.chunk(resumeText);

        assertThat(chunks).extracting(ResumeChunk::getType)
                .contains(ChunkType.CONTACT, ChunkType.EDUCATION, ChunkType.SKILL, ChunkType.EXPERIENCE, ChunkType.LINK);
        assertThat(chunks.stream()
                .filter(chunk -> chunk.getType() == ChunkType.SKILL)
                .map(ResumeChunk::getContent)
                .toList()).anySatisfy(content -> assertThat(content).contains("Spring Boot"));
        assertThat(chunks.stream()
                .filter(chunk -> chunk.getType() == ChunkType.EXPERIENCE)
                .map(ResumeChunk::getContent)
                .toList()).anySatisfy(content -> assertThat(content).contains("语义搜索"));

        chunks.forEach(chunk -> service.processAndSave(candidateId, chunk));

        verify(repository).saveContact(candidateId, "EMAIL", "zhangsan@example.com");
        verify(repository).saveContact(candidateId, "PHONE", "13800138000");
        verify(repository).saveEducation(candidateId, "清华大学", "计算机科学", "本科");
        verify(repository).saveLink(candidateId, "https://github.com/zhangsan");
    }

    @Test
    @DisplayName("shouldNotFailWhenOptionalFieldsAreMissing")
    void shouldNotFailWhenOptionalFieldsAreMissing() {
        String resumeText = """
                姓名: 李四
                邮箱: lisi@example.com

                技能

                Python FastAPI SQL

                项目经历

                负责招聘系统接口开发与联调。
                """;
        UUID candidateId = UUID.randomUUID();

        assertThat(nameExtractor.extract(resumeText)).isEqualTo("李四");

        List<ResumeChunk> chunks = chunker.chunk(resumeText);

        assertThat(chunks).extracting(ResumeChunk::getType)
                .contains(ChunkType.CONTACT, ChunkType.SKILL, ChunkType.EXPERIENCE);
        assertDoesNotThrow(() -> chunks.forEach(chunk -> service.processAndSave(candidateId, chunk)));

        verify(repository).saveContact(candidateId, "EMAIL", "lisi@example.com");
        verify(repository, never()).saveContact(candidateId, "PHONE", "13800138000");
        verify(repository, never()).saveEducation(eq(candidateId), anyString(), anyString(), anyString());
        verify(repository, never()).saveLink(eq(candidateId), anyString());
        verify(repository, never()).saveHonor(eq(candidateId), anyString());
    }

    private String loadResource(String path) throws Exception {
        try (InputStream inputStream = getClass().getResourceAsStream(path)) {
            assertThat(inputStream).isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
