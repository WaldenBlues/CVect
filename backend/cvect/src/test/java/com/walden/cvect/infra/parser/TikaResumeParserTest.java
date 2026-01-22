package com.walden.cvect.infra.parser;

import com.walden.cvect.model.ParseResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TikaResumeParserTest {

        @Autowired
        private TikaResumeParser parser;

        @Test
        void should_parse_pdf_resume_to_raw_text() throws Exception {
                // given
                InputStream is = getClass()
                                .getResourceAsStream("/static/My.pdf");

                assertNotNull(is, "Resume.pdf 不存在，请检查 src/main/resources/static");

                // when
                ParseResult result = parser.parse(is, "application/pdf");

                // then
                assertNotNull(result);
                assertNotNull(result.getContent());
                assertFalse(result.getContent().isBlank(), "解析结果不应为空");

                assertEquals("application/pdf", result.getContentType());

                assertTrue(result.getCharCount() > 200,
                                "简历文本长度过短，可能解析失败");

                // 非强语义校验（防止乱码）
                assertFalse(result.getContent().contains("\uFFFD"),
                                "解析结果包含替换字符，可能存在编码问题");

                // debug 输出（测试时很有用）
                System.out.println("========= RAW TEXT =========");
                System.out.println(result.getContent());
                System.out.println("============================");
        }
}
