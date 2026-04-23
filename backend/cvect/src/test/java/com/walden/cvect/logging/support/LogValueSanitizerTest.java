package com.walden.cvect.logging.support;

import com.walden.cvect.logging.config.LogProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;

class LogValueSanitizerTest {

    private final LogValueSanitizer sanitizer = new LogValueSanitizer(new LogProperties());

    @Test
    void escapesControlCharactersInStringSummaries() {
        String summary = sanitizer.summarizeReturnValue("line1\nline2\r\t\u0001x");

        assertThat(summary).isEqualTo("\"line1\\nline2\\r\\t\\u0001x\"");
    }

    @Test
    void escapesControlCharactersInMultipartFileNames() {
        MultipartFile[] files = new MultipartFile[] {
                new MockMultipartFile("files", "first\nname.txt", "text/plain", new byte[] {1}),
                new MockMultipartFile("files", "second\rname.txt", "text/plain", new byte[] {2})
        };

        String summary = sanitizer.summarizeReturnValue(files);

        assertThat(summary).isEqualTo(
                "MultipartFile[2]{totalBytes=2,names=\"first\\nname.txt|second\\rname.txt\"}");
    }
}
