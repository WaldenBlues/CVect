package com.walden.cvect.service;

import java.io.InputStream;
import java.util.List;

import org.springframework.stereotype.Service;
import com.walden.cvect.model.ResumeChunk;
import com.walden.cvect.infra.parser.ResumeParser;
import com.walden.cvect.infra.process.ResumeTextNormalizer;
import com.walden.cvect.model.ParseResult;

@Service
public class ResumeProcessService {

    private final ResumeParser parser;
    private final ResumeTextNormalizer normalizer;
    private final ChunkerService chunker;

    public ResumeProcessService(
            ResumeParser parser,
            ResumeTextNormalizer normalizer,
            ChunkerService chunker) {
        this.parser = parser;
        this.normalizer = normalizer;
        this.chunker = chunker;
    }

    public List<ResumeChunk> process(InputStream is, String contentType) {
        ParseResult parsed = parser.parse(is, contentType);
        String normalized = normalizer.normalize(parsed.getContent());
        return chunker.chunk(normalized);
    }
}
