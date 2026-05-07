package com.walden.cvect.infra.parser;

import com.walden.cvect.exception.ResumeParseException;
import com.walden.cvect.model.ParseResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TikaResumeParser resource tests")
class TikaResumeParserResourceTest {

    private final TikaResumeParser parser = new TikaResumeParser();

    @Test
    @DisplayName("shouldParseMinimalResumeTextFile")
    void shouldParseMinimalResumeTextFile() throws Exception {
        try (InputStream inputStream = getClass().getResourceAsStream("/resume/minimal-resume.txt")) {
            ParseResult result = parser.parse(inputStream, "text/plain");

            assertThat(result).isNotNull();
            assertThat(result.getContent()).isNotBlank();
            assertThat(result.getContent()).contains("张三", "zhangsan@example.com", "清华大学");
            assertThat(result.getCharCount()).isPositive();
            assertThat(result.getContentType()).isNotBlank();
        }
    }

    @Test
    @DisplayName("shouldReturnEmptyContentForEmptyResumeTextFile")
    void shouldReturnEmptyContentForEmptyResumeTextFile() throws Exception {
        try (InputStream inputStream = getClass().getResourceAsStream("/resume/empty-resume.txt")) {
            ParseResult result = parser.parse(inputStream, "text/plain");

            assertThat(result).isNotNull();
            assertThat(result.getContent()).isBlank();
            assertThat(result.getCharCount()).isEqualTo(result.getContent().length());
        }
    }

    @Test
    @DisplayName("shouldThrowResumeParseExceptionWhenInputStreamIsUnreadable")
    void shouldThrowResumeParseExceptionWhenInputStreamIsUnreadable() {
        InputStream failingStream = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("simulated unreadable file");
            }
        };

        assertThatThrownBy(() -> parser.parse(failingStream, "application/octet-stream"))
                .isInstanceOf(ResumeParseException.class)
                .hasMessageContaining("文件流处理或解析 IO 失败");
    }
}
