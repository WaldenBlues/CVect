package com.walden.cvect.infra.parser;

import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class TikaResumeParserTest {

    private final ResumeParser parser = new TikaResumeParser();

    @Test
    void should_parse_pdf_resume_to_text() throws Exception {
        InputStream is = getClass()
                .getClassLoader()
                .getResourceAsStream("resumes/sample.pdf");

        assertNotNull(is);

        String text = parser.parse(is, "application/pdf");

        assertNotNull(text);
        assertFalse(text.isBlank());

        // 弱断言：只验证“像简历”
        assertTrue(text.contains("Java") || text.contains("项目"));
    }

    @Test
    void should_parse_docx_resume_to_text() throws Exception {
        InputStream is = getClass()
                .getClassLoader()
                .getResourceAsStream("resumes/sample.docx");

        String text = parser.parse(is,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        assertFalse(text.isBlank());
    }
}
