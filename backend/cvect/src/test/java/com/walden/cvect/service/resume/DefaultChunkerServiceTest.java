package com.walden.cvect.service.resume;

import com.walden.cvect.model.ChunkType;
import com.walden.cvect.model.ResumeChunk;
import com.walden.cvect.service.resume.DefaultChunkerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DefaultChunkerService unit tests")
class DefaultChunkerServiceTest {

    @Test
    @DisplayName("should split long skill chunks into safe segments")
    void shouldSplitLongSkillChunks() {
        DefaultChunkerService chunker = new DefaultChunkerService(80, 10);
        String normalized = "技能\n\n" + "Java Spring Docker Kubernetes 微服务实践经验 ".repeat(12);

        List<ResumeChunk> chunks = chunker.chunk(normalized);

        assertTrue(chunks.size() >= 2);
        assertTrue(chunks.stream().allMatch(chunk -> chunk.getType() == ChunkType.SKILL));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.getLength() <= 80));
    }

    @Test
    @DisplayName("should keep short contact chunks untouched")
    void shouldKeepShortContactChunkUntouched() {
        DefaultChunkerService chunker = new DefaultChunkerService(80, 10);
        String normalized = "电话：13800138000 邮箱：demo@example.com";

        List<ResumeChunk> chunks = chunker.chunk(normalized);

        assertEquals(1, chunks.size());
        assertEquals(ChunkType.CONTACT, chunks.get(0).getType());
    }
}
