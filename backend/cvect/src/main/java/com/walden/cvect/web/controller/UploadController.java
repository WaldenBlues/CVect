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
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 简历上传 API（单/多文件 + ZIP 批量）
 */
@RestController
@RequestMapping("/api/uploads")
public class UploadController {
    private static final Logger log = LoggerFactory.getLogger(UploadController.class);

    private static final long MAX_ENTRY_BYTES = 20 * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 200 * 1024 * 1024;
    private static final List<String> ALLOWED_EXT = Arrays.asList(".pdf", ".doc", ".docx", ".md", ".txt");
    private static final List<UploadItemStatus> INFLIGHT_STATUSES =
            List.of(UploadItemStatus.QUEUED, UploadItemStatus.PROCESSING);

    private final JobDescriptionJpaRepository jobDescriptionRepository;
    private final UploadBatchJpaRepository batchRepository;
    private final UploadItemJpaRepository itemRepository;
    private final BatchStreamService batchStreamService;
    private final Path storageDir;
    private final int maxInflightItems;
    private final int maxFilesPerZip;
    private final ReentrantLock queueAdmissionLock = new ReentrantLock(true);
    private final AtomicLong inflightReservations = new AtomicLong(0L);

    public UploadController(JobDescriptionJpaRepository jobDescriptionRepository,
            UploadBatchJpaRepository batchRepository,
            UploadItemJpaRepository itemRepository,
            BatchStreamService batchStreamService,
            @Value("${app.upload.storage-dir:storage}") String storageDir,
            @Value("${app.upload.max-inflight-items:2000}") int maxInflightItems,
            @Value("${app.upload.max-files-per-zip:2000}") int maxFilesPerZip) {
        this.jobDescriptionRepository = jobDescriptionRepository;
        this.batchRepository = batchRepository;
        this.itemRepository = itemRepository;
        this.batchStreamService = batchStreamService;
        this.storageDir = resolveStorageDir(storageDir);
        this.maxInflightItems = Math.max(1, maxInflightItems);
        this.maxFilesPerZip = Math.max(1, maxFilesPerZip);
    }

    @PostConstruct
    void logLimits() {
        log.info("Upload limits: maxInflightItems={}, maxFilesPerZip={}, maxEntryBytesMB={}, maxTotalBytesMB={}",
                maxInflightItems,
                maxFilesPerZip,
                MAX_ENTRY_BYTES / (1024 * 1024),
                MAX_TOTAL_BYTES / (1024 * 1024));
    }

    @PostMapping("/resumes")
    public ResponseEntity<?> uploadResumes(
            @RequestParam("jdId") String jdId,
            @RequestParam("files") MultipartFile[] files) throws IOException {
        JobDescription jd = resolveJobDescription(jdId);
        if (jd == null) {
            return ResponseEntity.badRequest().build();
        }
        if (files == null || files.length == 0) {
            return ResponseEntity.badRequest().build();
        }
        int requestedSlots = Math.max(1, files.length);
        if (!tryReserveInflightSlots(requestedSlots)) {
            return ResponseEntity.status(429).build();
        }
        try {
            UploadBatch batch = batchRepository.save(new UploadBatch(jd, files.length));
            List<FileUploadResult> results = new ArrayList<>();

            for (MultipartFile file : files) {
                results.add(processUploadedFile(batch, file));
            }

            refreshBatchProgress(batch.getId());
            return ResponseEntity.ok(new BatchUploadResponse(batch.getId(), results));
        } finally {
            releaseInflightReservations(requestedSlots);
        }
    }

    @PostMapping("/zip")
    public ResponseEntity<?> uploadZip(
            @RequestParam("jdId") String jdId,
            @RequestParam("zipFile") MultipartFile zipFile) throws IOException {
        JobDescription jd = resolveJobDescription(jdId);
        if (jd == null || zipFile == null || zipFile.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (isQueueOverloaded(1)) {
            return ResponseEntity.status(429).build();
        }

        ensureStorage();

        UploadBatch batch = batchRepository.save(new UploadBatch(jd, 0));
        int totalFiles = 0;
        long totalBytes = 0;
        boolean truncated = false;

        try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                if (totalFiles >= maxFilesPerZip) {
                    truncated = true;
                    break;
                }
                totalFiles++;

                String fileName = entry.getName();
                if (!isAllowedExtension(fileName)) {
                    failRejected(batch, fileName, "Unsupported file type");
                    continue;
                }
                if (!tryReserveInflightSlots(1)) {
                    failRejected(batch, fileName, "Upload queue is busy, please retry later");
                    continue;
                }

                try {
                    Path storedPath = newStoragePath(fileName);
                    long entrySize;
                    try {
                        entrySize = writeEntryToFile(zis, storedPath);
                    } catch (IOException ex) {
                        Files.deleteIfExists(storedPath);
                        failRejected(batch, fileName, ex.getMessage());
                        continue;
                    }
                    totalBytes += entrySize;
                    if (entrySize > MAX_ENTRY_BYTES || totalBytes > MAX_TOTAL_BYTES) {
                        Files.deleteIfExists(storedPath);
                        failRejected(batch, fileName, "File size limit exceeded");
                        continue;
                    }

                    processStoredFile(batch, fileName, storedPath);
                } finally {
                    releaseInflightReservations(1);
                }
            }
        }

        batch.setTotalFiles(totalFiles);
        batchRepository.save(batch);
        refreshBatchProgress(batch.getId());
        return ResponseEntity.ok(new ZipUploadResponse(batch.getId(), totalFiles, truncated));
    }

    private FileUploadResult processUploadedFile(UploadBatch batch, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return failRejected(batch, null, "Empty file is not allowed");
        }
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
        UploadItem item = new UploadItem(batch, fileName);
        item.setStatus(UploadItemStatus.QUEUED);
        item.setStoragePath(storedPath.toString());
        item.setQueueJobKey(UploadQueueJobKeyGenerator.nextKey(item.getId()));
        item = itemRepository.save(item);
        publishBatchEvent(batch, item, null);
        return new FileUploadResult(fileName, null, "QUEUED", null);
    }

    private FileUploadResult failRejected(UploadBatch batch, String fileName, String reason) {
        UploadItem item = new UploadItem(batch, fileName);
        item.setStatus(UploadItemStatus.FAILED);
        item.setErrorMessage(reason);
        item = itemRepository.save(item);
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
        batchRepository.refreshProgressFromItems(batchId);
        UploadBatch batch = batchRepository.findById(batchId).orElse(null);
        if (batch == null) {
            return;
        }
        int totalFiles = batch.getTotalFiles() == null ? 0 : Math.max(0, batch.getTotalFiles());
        int processedFiles = batch.getProcessedFiles() == null ? 0 : Math.max(0, batch.getProcessedFiles());
        String batchStatus = batch.getStatus() == null ? UploadBatchStatus.PROCESSING.name() : batch.getStatus().name();
        batchStreamService.publish(batchId, new BatchStreamEvent(
                batchId,
                batchStatus,
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

    public record ZipUploadResponse(UUID batchId, int totalFiles, boolean truncated) {}

    public record FileUploadResult(String fileName, UUID candidateId, String status, String errorMessage) {}

    private void ensureStorage() throws IOException {
        Files.createDirectories(storageDir);
    }

    private Path newStoragePath(String fileName) {
        String ext = "";
        if (fileName != null) {
            int dot = fileName.lastIndexOf('.');
            if (dot >= 0) {
                ext = fileName.substring(dot).toLowerCase(Locale.ROOT);
            }
        }
        return storageDir.resolve(UUID.randomUUID() + ext);
    }

    private boolean isAllowedExtension(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (String ext : ALLOWED_EXT) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private boolean isQueueOverloaded(int incomingItems) {
        return inflightItemCount() + inflightReservations.get() + Math.max(1, incomingItems) > maxInflightItems;
    }

    private long inflightItemCount() {
        return itemRepository.countByStatusIn(INFLIGHT_STATUSES);
    }

    private boolean tryReserveInflightSlots(int slots) {
        int normalized = Math.max(1, slots);
        queueAdmissionLock.lock();
        try {
            if (isQueueOverloaded(normalized)) {
                return false;
            }
            inflightReservations.addAndGet(normalized);
            return true;
        } finally {
            queueAdmissionLock.unlock();
        }
    }

    private void releaseInflightReservations(int slots) {
        int normalized = Math.max(1, slots);
        inflightReservations.updateAndGet(current -> Math.max(0L, current - normalized));
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

    private static Path resolveStorageDir(String rawDir) {
        String normalized = Objects.requireNonNullElse(rawDir, "").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("app.upload.storage-dir must not be blank");
        }
        return Paths.get(normalized).toAbsolutePath().normalize();
    }
}
