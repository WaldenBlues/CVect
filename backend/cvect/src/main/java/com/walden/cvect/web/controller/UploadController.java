package com.walden.cvect.web.controller;

import com.walden.cvect.model.entity.JobDescription;
import com.walden.cvect.model.entity.UploadBatch;
import com.walden.cvect.model.entity.UploadBatchStatus;
import com.walden.cvect.model.entity.UploadItem;
import com.walden.cvect.model.entity.UploadItemStatus;
import com.walden.cvect.repository.JobDescriptionJpaRepository;
import com.walden.cvect.repository.UploadBatchJpaRepository;
import com.walden.cvect.repository.UploadItemJpaRepository;
import com.walden.cvect.service.UploadQueueJobKeyGenerator;
import com.walden.cvect.web.sse.BatchStreamEvent;
import com.walden.cvect.web.sse.BatchStreamService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 简历上传 API（单/多文件 + ZIP 批量）
 */
@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private static final long MAX_ENTRY_BYTES = 20 * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 200 * 1024 * 1024;
    private static final int MAX_FILES = 200;
    private static final List<String> ALLOWED_EXT = Arrays.asList(".pdf", ".doc", ".docx", ".md", ".txt");

    private final JobDescriptionJpaRepository jobDescriptionRepository;
    private final UploadBatchJpaRepository batchRepository;
    private final UploadItemJpaRepository itemRepository;
    private final BatchStreamService batchStreamService;
    private final Path storageDir;

    public UploadController(JobDescriptionJpaRepository jobDescriptionRepository,
            UploadBatchJpaRepository batchRepository,
            UploadItemJpaRepository itemRepository,
            BatchStreamService batchStreamService,
            @Value("${app.upload.storage-dir:storage}") String storageDir) {
        this.jobDescriptionRepository = jobDescriptionRepository;
        this.batchRepository = batchRepository;
        this.itemRepository = itemRepository;
        this.batchStreamService = batchStreamService;
        this.storageDir = resolveStorageDir(storageDir);
    }

    @PostMapping("/resumes")
    public ResponseEntity<BatchUploadResponse> uploadResumes(
            @RequestParam("jdId") String jdId,
            @RequestParam("files") MultipartFile[] files) throws IOException {
        JobDescription jd = resolveJobDescription(jdId);
        if (jd == null) {
            return ResponseEntity.badRequest().build();
        }
        if (files == null || files.length == 0) {
            return ResponseEntity.badRequest().build();
        }

        UploadBatch batch = batchRepository.save(new UploadBatch(jd, files.length));
        List<FileUploadResult> results = new ArrayList<>();

        for (MultipartFile file : files) {
            results.add(processUploadedFile(batch, file));
        }

        refreshBatchProgress(batch.getId());
        return ResponseEntity.ok(new BatchUploadResponse(batch.getId(), results));
    }

    @PostMapping("/zip")
    public ResponseEntity<ZipUploadResponse> uploadZip(
            @RequestParam("jdId") String jdId,
            @RequestParam("zipFile") MultipartFile zipFile) throws IOException {
        JobDescription jd = resolveJobDescription(jdId);
        if (jd == null || zipFile == null) {
            return ResponseEntity.badRequest().build();
        }

        ensureStorage();

        UploadBatch batch = batchRepository.save(new UploadBatch(jd, 0));
        int totalFiles = 0;
        long totalBytes = 0;

        try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                if (totalFiles >= MAX_FILES) {
                    break;
                }
                totalFiles++;
                batch.setTotalFiles(totalFiles);
                batchRepository.save(batch);

                String fileName = entry.getName();
                if (!isAllowedExtension(fileName)) {
                    failRejected(batch, fileName, "Unsupported file type");
                    continue;
                }

                Path storedPath = newStoragePath(fileName);
                long entrySize = writeEntryToFile(zis, storedPath);
                totalBytes += entrySize;
                if (entrySize > MAX_ENTRY_BYTES || totalBytes > MAX_TOTAL_BYTES) {
                    Files.deleteIfExists(storedPath);
                    failRejected(batch, fileName, "File size limit exceeded");
                    continue;
                }

                processStoredFile(batch, fileName, storedPath);
            }
        }

        batch.setTotalFiles(totalFiles);
        batchRepository.save(batch);
        refreshBatchProgress(batch.getId());
        return ResponseEntity.ok(new ZipUploadResponse(batch.getId(), totalFiles));
    }

    private FileUploadResult processUploadedFile(UploadBatch batch, MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        if (!isAllowedExtension(fileName)) {
            return failRejected(batch, fileName, "Unsupported file type");
        }

        ensureStorage();
        Path storedPath = newStoragePath(fileName);
        try (InputStream in = file.getInputStream()) {
            copyToFileWithLimit(in, storedPath, MAX_ENTRY_BYTES);
            return processStoredFile(batch, fileName, storedPath);
        } catch (IOException e) {
            Files.deleteIfExists(storedPath);
            return failRejected(batch, fileName, e.getMessage());
        }
    }

    private FileUploadResult processStoredFile(UploadBatch batch, String fileName, Path storedPath) {
        UploadItem item = itemRepository.save(new UploadItem(batch, fileName));
        item.setStatus(UploadItemStatus.QUEUED);
        item.setStoragePath(storedPath.toString());
        item.setQueueJobKey(UploadQueueJobKeyGenerator.nextKey(item.getId()));
        itemRepository.save(item);
        publishBatchEvent(batch, item, null);
        return new FileUploadResult(fileName, null, "QUEUED", null);
    }

    private FileUploadResult failRejected(UploadBatch batch, String fileName, String reason) {
        UploadItem item = itemRepository.save(new UploadItem(batch, fileName));
        item.setStatus(UploadItemStatus.FAILED);
        item.setErrorMessage(reason);
        itemRepository.save(item);
        publishBatchEvent(batch, item, reason);
        return new FileUploadResult(fileName, null, "FAILED", reason);
    }

    private void publishBatchEvent(UploadBatch batch, UploadItem item, String error) {
        batchStreamService.publish(batch.getId(), new BatchStreamEvent(
                batch.getId(),
                item.getStatus().name(),
                batch.getTotalFiles(),
                batch.getProcessedFiles(),
                item.getFileName(),
                item.getCandidateId(),
                error,
                LocalDateTime.now()
        ));
    }

    private void refreshBatchProgress(UUID batchId) {
        UploadBatch batch = batchRepository.findById(batchId).orElse(null);
        if (batch == null) {
            return;
        }

        Map<UploadItemStatus, Long> countsByStatus = new EnumMap<>(UploadItemStatus.class);
        itemRepository.countGroupedByStatus(batchId).forEach(count ->
                countsByStatus.put(count.getStatus(), count.getCount()));

        long done = get(countsByStatus, UploadItemStatus.DONE);
        long duplicate = get(countsByStatus, UploadItemStatus.DUPLICATE);
        long failed = get(countsByStatus, UploadItemStatus.FAILED);
        long processing = get(countsByStatus, UploadItemStatus.PROCESSING) + get(countsByStatus, UploadItemStatus.RETRYING);
        long queued = get(countsByStatus, UploadItemStatus.QUEUED) + get(countsByStatus, UploadItemStatus.PENDING);

        int processedFiles = toInt(done + duplicate + failed);
        int totalFiles = batch.getTotalFiles() == null || batch.getTotalFiles() <= 0
                ? toInt(itemRepository.countByBatch_Id(batchId))
                : batch.getTotalFiles();

        batch.setProcessedFiles(processedFiles);
        if (processing == 0 && queued == 0 && processedFiles >= totalFiles) {
            batch.setStatus(UploadBatchStatus.DONE);
        } else {
            batch.setStatus(UploadBatchStatus.PROCESSING);
        }
        batchRepository.save(batch);
        batchStreamService.publish(batchId, new BatchStreamEvent(
                batchId,
                batch.getStatus().name(),
                totalFiles,
                processedFiles,
                null,
                null,
                null,
                LocalDateTime.now()
        ));
    }

    private JobDescription resolveJobDescription(String jdId) {
        if (jdId == null || jdId.isBlank()) {
            return null;
        }
        try {
            UUID id = UUID.fromString(jdId);
            return jobDescriptionRepository.findById(id).orElse(null);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public record BatchUploadResponse(UUID batchId, List<FileUploadResult> files) {}

    public record ZipUploadResponse(UUID batchId, int totalFiles) {}

    public record FileUploadResult(String fileName, UUID candidateId, String status, String errorMessage) {}

    private void ensureStorage() throws IOException {
        Files.createDirectories(storageDir);
    }

    private Path newStoragePath(String fileName) {
        String ext = "";
        if (fileName != null) {
            int dot = fileName.lastIndexOf('.');
            if (dot >= 0) {
                ext = fileName.substring(dot).toLowerCase();
            }
        }
        return storageDir.resolve(UUID.randomUUID() + ext);
    }

    private boolean isAllowedExtension(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase();
        for (String ext : ALLOWED_EXT) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private long writeEntryToFile(ZipInputStream zis, Path target) throws IOException {
        return copyToFileWithLimit(zis, target, MAX_ENTRY_BYTES);
    }

    private long copyToFileWithLimit(InputStream in, Path target, long maxBytes) throws IOException {
        long total = 0;
        try (OutputStream out = Files.newOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException("File size limit exceeded");
                }
                out.write(buffer, 0, read);
            }
        }
        return total;
    }

    private static long get(Map<UploadItemStatus, Long> counts, UploadItemStatus key) {
        return counts.getOrDefault(key, 0L);
    }

    private static int toInt(long value) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, value));
    }

    private static Path resolveStorageDir(String rawDir) {
        String normalized = Objects.requireNonNullElse(rawDir, "").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("app.upload.storage-dir must not be blank");
        }
        return Paths.get(normalized).toAbsolutePath().normalize();
    }
}
