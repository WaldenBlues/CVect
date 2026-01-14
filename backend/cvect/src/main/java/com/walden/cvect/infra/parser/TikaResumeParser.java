package com.walden.cvect.infra.parser;

import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class TikaResumeParser implements ResumeParser {

    private final Tika tika = new Tika();

    @Override
    public String parse(InputStream inputStream, String contentType) {
        try {
            Metadata metadata = new Metadata();
            if (contentType != null) {
                metadata.set(Metadata.CONTENT_TYPE, contentType);
            }
            return tika.parseToString(inputStream, metadata);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse resume file", e);
        }
    }
}
