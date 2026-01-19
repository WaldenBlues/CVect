package com.walden.cvect.model.fact;

import com.walden.cvect.infra.parser.ResumeParser;
import com.walden.cvect.infra.process.ResumeTextNormalizer;
import com.walden.cvect.model.ChunkType;
import com.walden.cvect.model.ParseResult; // 修正导入
import com.walden.cvect.model.ResumeChunk;
import com.walden.cvect.service.ChunkerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
@Tag("pipeline")
@SpringBootTest // 必须添加，以注入 Parser, Normalizer 和 ChunkerService
class FactExtractionIntegrationTest {

        @Autowired
        private ResumeParser parser;

        @Autowired
        private ResumeTextNormalizer normalizer;

        @Autowired
        private ChunkerService chunker;

        @Test
        @DisplayName("全链路测试：从 PDF 到事实层提取")
        void should_extract_facts_from_real_pdf_resume() throws Exception {
                // 1. Given: 加载真实文件并初始化选择器
                InputStream is = getClass().getResourceAsStream("/static/Resume.pdf");
                assertNotNull(is, "Resume.pdf 不存在，请检查 src/main/resources/static");

                // 使用你提供的 RuleBasedFactChunkSelector 和默认规则集
                FactChunkSelector selector = new RuleBasedFactChunkSelector(DefaultFactRules.RULES);

                // 2. When: 执行全链路流水线

                // A. 解析 PDF
                ParseResult parsed = parser.parse(is, "application/pdf");
                assertNotNull(parsed.getContent(), "解析内容不应为空");

                // B. 文本归一化
                String normalized = normalizer.normalize(parsed.getContent());

                // C. 分块处理
                List<ResumeChunk> chunks = chunker.chunk(normalized);
                assertFalse(chunks.isEmpty(), "分块结果不应为空");

                // D. 核心测试点：应用事实过滤规则
                List<ResumeChunk> facts = chunks.stream()
                                .filter(selector::accept)
                                .collect(Collectors.toList());

                // 3. Then: 验证集成结果
                assertNotNull(facts);

                // 验证：事实层应过滤掉无意义信息（如 HEADER）
                boolean hasHeader = facts.stream().anyMatch(c -> c.getType() == ChunkType.HEADER);
                assertFalse(hasHeader, "HeaderNeverFactRule 应该拒绝所有 HEADER 类型的 chunk");

                assertTrue(chunks.stream().anyMatch(c -> c.getType() == ChunkType.CONTACT),
                                "测试用 PDF 必须包含 CONTACT chunk");

                // 打印结果：观察 RuleBasedFactChunkSelector 的过滤效果
                printFactReport(chunks.size(), facts);
        }

        private void printFactReport(int originalSize, List<ResumeChunk> facts) {
                System.out.println("========= FACT EXTRACTION REPORT =========");
                System.out.println("原始分块总数: " + originalSize);
                System.out.println("提取事实总数: " + facts.size());
                System.out.println("-----------------------------------------");
                facts.forEach(f -> {
                        System.out.printf("[%s] -> %s%n", f.getType(),
                                        f.getContent().replace("\n", " ").substring(0,
                                                        Math.min(f.getContent().length(), 50)) + "...");
                });
                System.out.println("==========================================");
        }
}