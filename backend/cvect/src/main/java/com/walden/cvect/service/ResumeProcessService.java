package com.walden.cvect.service;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.walden.cvect.exception.ResumeProcessingException;
import com.walden.cvect.model.ResumeChunk;
import com.walden.cvect.infra.parser.ResumeParser;
import com.walden.cvect.infra.process.ResumeTextNormalizer;
import com.walden.cvect.model.ParseResult;

@Service
public class ResumeProcessService {

    private static final Logger log = LoggerFactory.getLogger(ResumeProcessService.class);

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

    /**
     * 解析简历全文：parse → normalize → chunk → extract facts
     */
    public ProcessResult process(InputStream is, String contentType) {
        try {
            ParseResult parsed = parser.parse(is, contentType);
            String normalized = normalizer.normalize(parsed.getContent());

            List<ResumeChunk> chunks = chunker.chunk(normalized);

            UUID candidateId = UUID.randomUUID();
            for (ResumeChunk chunk : chunks) {
                try {
                    factService.processAndSave(candidateId, chunk);
                } catch (Exception e) {
                    log.warn("Failed to process chunk: type={}, index={}", chunk.getType(), chunk.getIndex(), e);
                }
            }

            return new ProcessResult(candidateId, chunks);
        } catch (Exception e) {
            throw new ResumeProcessingException("Failed to process resume", e);
        }
    }

    public record ProcessResult(UUID candidateId, List<ResumeChunk> chunks) {
    }
}
