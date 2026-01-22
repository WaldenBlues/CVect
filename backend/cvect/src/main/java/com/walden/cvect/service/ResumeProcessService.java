package com.walden.cvect.service;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

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
    private final ResumeFactService factService;

    public ResumeProcessService(
            ResumeParser parser,
            ResumeTextNormalizer normalizer,
            ChunkerService chunker,
            ResumeFactService factService) {
        this.parser = parser;
        this.normalizer = normalizer;
        this.chunker = chunker;
        this.factService = factService;
    }

    public ProcessResult process(InputStream is, String contentType) {
        ParseResult parsed = parser.parse(is, contentType);
        String normalized = normalizer.normalize(parsed.getContent());

        List<ResumeChunk> chunks = chunker.chunk(normalized);

        UUID candidateId = UUID.randomUUID();
        for (ResumeChunk chunk : chunks) {
            factService.processAndSave(candidateId, chunk);
        }

        return new ProcessResult(candidateId, chunks);
    }

    public record ProcessResult(UUID candidateId, List<ResumeChunk> chunks) {
    }
}
