package com.walden.cvect.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.walden.cvect.exception.ResumeProcessingException;
import com.walden.cvect.model.ChunkType;
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
    private final VectorIngestService vectorIngestService;

    public ResumeProcessService(
            ResumeParser parser,
            ResumeTextNormalizer normalizer,
            NameExtractor nameExtractor,
            ChunkerService chunker,
            ResumeFactService factService,
            CandidateJpaRepository candidateRepository,
            JobDescriptionJpaRepository jobDescriptionRepository,
            CandidateSnapshotService snapshotService,
            CandidateStreamService streamService,
            VectorIngestService vectorIngestService) {
        this.parser = parser;
        this.normalizer = normalizer;
        this.nameExtractor = nameExtractor;
        this.chunker = chunker;
        this.factService = factService;
        this.candidateRepository = candidateRepository;
        this.jobDescriptionRepository = jobDescriptionRepository;
        this.snapshotService = snapshotService;
        this.streamService = streamService;
        this.vectorIngestService = vectorIngestService;
    }

    /**
     * 解析简历全文：parse → normalize → chunk → extract facts
     */
    public ProcessResult process(InputStream is, String contentType, String sourceFileName, Long fileSizeBytes, UUID jdId) {
        try {
            Path tempFile = Files.createTempFile("cvect-resume-process-", ".bin");
            try {
                copyToFile(Objects.requireNonNull(is, "input stream must not be null"), tempFile);
                long resolvedFileSize = fileSizeBytes != null ? fileSizeBytes : Files.size(tempFile);
                return processInternal(tempFile, contentType, sourceFileName, resolvedFileSize, jdId);
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } catch (Exception e) {
            throw wrapProcessingException(e);
        }
    }

    public ProcessResult process(Path sourcePath, String contentType, String sourceFileName, Long fileSizeBytes, UUID jdId) {
        try {
            return processInternal(
                    Objects.requireNonNull(sourcePath, "source path must not be null"),
                    contentType,
                    sourceFileName,
                    fileSizeBytes,
                    jdId);
        } catch (Exception e) {
            throw wrapProcessingException(e);
        }
    }

    private ProcessResult processInternal(
            Path sourcePath,
            String contentType,
            String sourceFileName,
            Long fileSizeBytes,
            UUID jdId) throws IOException {
        Path normalizedPath = sourcePath.toAbsolutePath().normalize();
        String fileHash = sha256Hex(normalizedPath);
        JobDescription jobDescription = resolveJobDescription(jdId);
        Candidate existing = findExistingCandidate(fileHash, jdId);

        ParseResult parsed = parse(normalizedPath, contentType);
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
    }

    private ParseResult parse(Path sourcePath, String contentType) throws IOException {
        try (InputStream input = Files.newInputStream(sourcePath)) {
            return Objects.requireNonNull(
                    parser.parse(input, contentType),
                    "resume parser returned null result");
        }
    }

    private Candidate findExistingCandidate(String fileHash, UUID jdId) {
        if (jdId != null) {
            return candidateRepository.findByFileHashAndJobDescriptionId(fileHash, jdId).orElse(null);
        }
        return candidateRepository.findByFileHashAndJobDescriptionIsNull(fileHash).orElse(null);
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

    private void copyToFile(InputStream input, Path target) throws IOException {
        try (OutputStream output = Files.newOutputStream(target)) {
            input.transferTo(output);
        }
    }

    private String sha256Hex(Path sourcePath) throws IOException {
        try {
            MessageDigest digest = newSha256Digest();
            try (InputStream input = new DigestInputStream(Files.newInputStream(sourcePath), digest)) {
                input.transferTo(OutputStream.nullOutputStream());
            }
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new ResumeProcessingException("SHA-256 not available", e);
        }
    }

    private MessageDigest newSha256Digest() throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA-256");
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private ResumeProcessingException wrapProcessingException(Exception e) {
        if (e instanceof ResumeProcessingException processingException) {
            return processingException;
        }
        return new ResumeProcessingException("Failed to process resume", e);
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
            if (!isVectorizableChunk(chunk)) {
                continue;
            }
            vectorIngestService.ingest(candidateId, chunk.getType(), chunk.getContent());
        }
    }

    private static boolean isVectorizableChunk(ResumeChunk chunk) {
        if (chunk == null) {
            return false;
        }
        if (chunk.getType() != ChunkType.EXPERIENCE && chunk.getType() != ChunkType.SKILL) {
            return false;
        }
        String content = chunk.getContent();
        return content != null && !content.isBlank();
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
