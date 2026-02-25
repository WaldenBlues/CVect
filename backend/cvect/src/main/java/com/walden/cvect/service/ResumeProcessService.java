package com.walden.cvect.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.walden.cvect.exception.ResumeProcessingException;
import com.walden.cvect.model.ResumeChunk;
import com.walden.cvect.infra.parser.ResumeParser;
import com.walden.cvect.infra.process.NameExtractor;
import com.walden.cvect.infra.process.ResumeTextNormalizer;
import com.walden.cvect.model.ParseResult;
import com.walden.cvect.model.entity.Candidate;
import com.walden.cvect.model.entity.JobDescription;
import com.walden.cvect.repository.CandidateJpaRepository;
import com.walden.cvect.repository.JobDescriptionJpaRepository;
import com.walden.cvect.web.stream.CandidateStreamEvent;
import com.walden.cvect.web.stream.CandidateStreamService;

@Service
public class ResumeProcessService {

    private static final Logger log = LoggerFactory.getLogger(ResumeProcessService.class);

    private final ResumeParser parser;
    private final ResumeTextNormalizer normalizer;
    private final NameExtractor nameExtractor;
    private final ChunkerService chunker;
    private final ResumeFactService factService;
    private final CandidateJpaRepository candidateRepository;
    private final JobDescriptionJpaRepository jobDescriptionRepository;
    private final CandidateSnapshotService snapshotService;
    private final CandidateStreamService streamService;

    public ResumeProcessService(
            ResumeParser parser,
            ResumeTextNormalizer normalizer,
            NameExtractor nameExtractor,
            ChunkerService chunker,
            ResumeFactService factService,
            CandidateJpaRepository candidateRepository,
            JobDescriptionJpaRepository jobDescriptionRepository,
            CandidateSnapshotService snapshotService,
            CandidateStreamService streamService) {
        this.parser = parser;
        this.normalizer = normalizer;
        this.nameExtractor = nameExtractor;
        this.chunker = chunker;
        this.factService = factService;
        this.candidateRepository = candidateRepository;
        this.jobDescriptionRepository = jobDescriptionRepository;
        this.snapshotService = snapshotService;
        this.streamService = streamService;
    }

    /**
     * 解析简历全文：parse → normalize → chunk → extract facts
     */
    public ProcessResult process(InputStream is, String contentType, String sourceFileName, Long fileSizeBytes, UUID jdId) {
        try {
            byte[] fileBytes = readAllBytes(Objects.requireNonNull(is, "input stream must not be null"));
            String fileHash = sha256Hex(fileBytes);

            Candidate existing = candidateRepository.findByFileHash(fileHash).orElse(null);
            JobDescription jobDescription = resolveJobDescription(jdId);

            ParseResult parsed = Objects.requireNonNull(
                    parser.parse(new ByteArrayInputStream(fileBytes), contentType),
                    "resume parser returned null result");
            String normalized = normalizer.normalize(Objects.requireNonNullElse(parsed.getContent(), ""));
            String extractedName = nameExtractor.extract(normalized);
            List<ResumeChunk> chunks = chunker.chunk(normalized);
            UUID candidateId;
            boolean duplicated = existing != null;
            if (existing != null) {
                candidateId = existing.getId();
                if (extractedName != null && !extractedName.isBlank()
                        && !extractedName.equals(existing.getName())) {
                    existing.setName(extractedName);
                }
                if (jobDescription != null && existing.getJobDescription() == null) {
                    existing.setJobDescription(jobDescription);
                }
                candidateRepository.save(existing);
            } else {
                candidateId = persistCandidate(parsed, sourceFileName, fileSizeBytes, fileHash, extractedName, jobDescription);
            }

            if (existing == null) {
                processChunks(candidateId, chunks);
            } else {
                log.info("Duplicate file detected, skipping persistence. fileHash={}, candidateId={}", fileHash, candidateId);
            }
            publishCandidateEvent(candidateId, duplicated ? "DUPLICATE" : "DONE");

            return new ProcessResult(candidateId, chunks, duplicated, fileHash);
        } catch (Exception e) {
            throw new ResumeProcessingException("Failed to process resume", e);
        }
    }

    /**
     * 解析元数据入库，返回候选人 ID
     */
    private UUID persistCandidate(ParseResult parsed,
            String sourceFileName,
            Long fileSizeBytes,
            String fileHash,
            String name,
            JobDescription jobDescription) {
        Candidate candidate = new Candidate(
                sourceFileName,
                fileHash,
                name,
                jobDescription,
                parsed.getContentType(),
                fileSizeBytes,
                parsed.getCharCount(),
                parsed.isTruncated());
        Candidate saved = candidateRepository.save(candidate);
        return saved.getId();
    }

    private JobDescription resolveJobDescription(UUID jdId) {
        if (jdId == null) {
            return null;
        }
        return jobDescriptionRepository.findById(jdId).orElse(null);
    }

    private byte[] readAllBytes(InputStream is) throws IOException {
        return is.readAllBytes();
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new ResumeProcessingException("SHA-256 not available", e);
        }
    }

    /**
     * 逐块提取事实，失败不影响整体流程
     */
    private void processChunks(UUID candidateId, List<ResumeChunk> chunks) {
        for (ResumeChunk chunk : chunks) {
            try {
                factService.processAndSave(candidateId, chunk);
            } catch (Exception e) {
                log.warn("Failed to process chunk: type={}, index={}", chunk.getType(), chunk.getIndex(), e);
            }
        }
    }

    /**
     * 发送候选人入库事件（用于前端实时展示）
     */
    private void publishCandidateEvent(UUID candidateId, String status) {
        CandidateStreamEvent event = snapshotService.build(candidateId, status);
        if (event == null) {
            return;
        }
        streamService.publish(event);
    }

    public record ProcessResult(UUID candidateId, List<ResumeChunk> chunks, boolean duplicated, String fileHash) {
    }
}
