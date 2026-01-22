package com.walden.cvect.service;

import com.walden.cvect.infra.parser.ResumeParser;
import com.walden.cvect.infra.process.ResumeTextNormalizer;
import com.walden.cvect.model.ChunkType;
import com.walden.cvect.model.ParseResult;
import com.walden.cvect.model.ResumeChunk;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ResumeChunkPipelineTest {

        @Autowired
        private ResumeParser parser;

        @Autowired
        private ResumeTextNormalizer normalizer;

        @Autowired
        private ChunkerService chunker;

        @Test
        void should_parse_and_chunk_real_pdf_resume() throws Exception {
                // given
                InputStream is = getClass()
                                .getResourceAsStream("/static/My.pdf");

                assertNotNull(is, "Resume.pdf 不存在，请检查 src/main/resources/static");

                // when: parse
                ParseResult parsed = parser.parse(is, "application/pdf");

                String normalized = normalizer.normalize(parsed.getContent());

                List<ResumeChunk> chunks = chunker.chunk(normalized);

                // then: basic sanity
                assertNotNull(chunks);
                assertFalse(chunks.isEmpty(), "chunk 结果不应为空");

                // chunk 内容与顺序
                for (ResumeChunk chunk : chunks) {
                        assertNotNull(chunk.getContent());
                        assertFalse(chunk.getContent().isBlank(),
                                        "chunk 内容不应为空: " + chunk.getType());

                        switch (chunk.getType()) {
                                case CONTACT:
                                case LINK:
                                        // 只要求“存在”
                                        assertTrue(chunk.getLength() >= 1,
                                                        "引用型 chunk 不应为空: " + chunk.getType());
                                        break;

                                case HEADER:
                                        assertTrue(chunk.getLength() >= 5,
                                                        "HEADER chunk 过短");
                                        break;
                                default:
                                        break;
                        }
                }

                // 至少应包含非 OTHER 的 chunk
                boolean hasSemanticChunk = chunks.stream()
                                .anyMatch(c -> c.getType() != ChunkType.OTHER);

                assertTrue(hasSemanticChunk,
                                "chunk 类型全部为 OTHER，说明 chunk 规则可能失效");

                // debug 输出（非常有价值）
                System.out.println("========= CHUNKS =========");
                for (ResumeChunk chunk : chunks) {
                        System.out.println("[" + chunk.getIndex() + "] "
                                        + chunk.getType());
                        System.out.println(chunk.getContent());
                        System.out.println("-------------------------");
                }
                System.out.println("==========================");
        }
}
