package com.walden.cvect.service;

import com.walden.cvect.model.entity.UploadBatch;
import com.walden.cvect.model.entity.UploadItem;
import com.walden.cvect.model.entity.UploadItemStatus;
import com.walden.cvect.repository.UploadBatchJpaRepository;
import com.walden.cvect.repository.UploadItemJpaRepository;
import com.walden.cvect.web.sse.BatchStreamEvent;
import com.walden.cvect.web.sse.BatchStreamService;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 消费 upload_items.QUEUED 的轻量 DB worker
 */
@Service
@ConditionalOnProperty(name = "app.upload.worker.enabled", havingValue = "true", matchIfMissing = true)
public class UploadQueueWorkerService {

    private static final Logger log = LoggerFactory.getLogger(UploadQueueWorkerService.class);
    private static final String STALE_RECOVERY_MESSAGE = "Recovered stale queue lease";

    private final UploadItemJpaRepository itemRepository;
    private final UploadBatchJpaRepository batchRepository;
    private final ResumeProcessService resumeProcessService;
    private final BatchStreamService batchStreamService;
    private final TransactionTemplate requiresNewTx;
    private final Duration staleProcessingTimeout;
    private final Path storageDir;
    private final int claimBatchSize;
    private final long maintenanceIntervalMs;
    private final AtomicLong lastMaintenanceAtMs = new AtomicLong(0L);

    public UploadQueueWorkerService(
            UploadItemJpaRepository itemRepository,
            UploadBatchJpaRepository batchRepository,
            ResumeProcessService resumeProcessService,
            BatchStreamService batchStreamService,
            PlatformTransactionManager transactionManager,
            @Value("${app.upload.worker.stale-processing-ms:300000}") long staleProcessingMs,
            @Value("${app.upload.storage-dir:storage}") String storageDir,
            @Value("${app.upload.worker.claim-batch-size:100}") int claimBatchSize,
            @Value("${app.upload.worker.maintenance-interval-ms:5000}") long maintenanceIntervalMs) {
        this.itemRepository = itemRepository;
        this.batchRepository = batchRepository;
        this.resumeProcessService = resumeProcessService;
        this.batchStreamService = batchStreamService;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.staleProcessingTimeout = Duration.ofMillis(Math.max(0L, staleProcessingMs));
        this.storageDir = resolveStorageDir(storageDir);
        this.claimBatchSize = Math.max(1, Math.min(claimBatchSize, 500));
        this.maintenanceIntervalMs = Math.max(1000L, maintenanceIntervalMs);
    }

    public int consumeQueuedItems() {
        runMaintenanceIfDue();

        List<UUID> claimedItemIds = inTx(() -> itemRepository.claimNextQueuedBatch(claimBatchSize), List.of());
        if (claimedItemIds.isEmpty()) {
            return 0;
        }

        for (UUID itemId : claimedItemIds) {
            inTxNoResult(() -> processClaimedItem(itemId));
        }
        return claimedItemIds.size();
    }

    private void runMaintenanceIfDue() {
        long now = System.currentTimeMillis();
        long last = lastMaintenanceAtMs.get();
        if (now - last < maintenanceIntervalMs) {
            return;
        }
        if (!lastMaintenanceAtMs.compareAndSet(last, now)) {
            return;
        }
        recoverStaleProcessingItems();
        repairQueuedItemsWithoutJobKey();
    }

    void recoverStaleProcessingItems() {
        LocalDateTime threshold = LocalDateTime.now().minus(staleProcessingTimeout);
        Set<UUID> affectedBatchIds = new HashSet<>();
        int recoveredCount = 0;
        while (true) {
            List<UploadItem> staleItems = itemRepository.findTop50ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                    UploadItemStatus.PROCESSING,
                    threshold
            );
            if (staleItems.isEmpty()) {
                break;
            }
            int batchRecovered = 0;
            for (UploadItem stale : staleItems) {
                String oldJobKey = normalizeJobKey(stale.getQueueJobKey());
                String newJobKey = UploadQueueJobKeyGenerator.nextKey(stale.getId());
                boolean recovered = inTx(() -> itemRepository.recoverStaleProcessingLease(
                        stale.getId(),
                        oldJobKey,
                        newJobKey,
                        STALE_RECOVERY_MESSAGE,
                        UploadItemStatus.QUEUED,
                        UploadItemStatus.PROCESSING) > 0, false);
                if (!recovered) {
                    continue;
                }
                affectedBatchIds.add(stale.getBatch().getId());
                batchRecovered++;
            }
            recoveredCount += batchRecovered;
            if (batchRecovered == 0) {
                break;
            }
        }

        if (!affectedBatchIds.isEmpty()) {
            log.info("Recovered {} stale queue items", recoveredCount);
            for (UUID batchId : affectedBatchIds) {
                inTxNoResult(() -> refreshBatchProgress(batchId));
            }
        }
    }

    private <T> T inTx(TxSupplier<T> supplier, T fallback) {
        T result = requiresNewTx.execute(status -> supplier.get());
        return result == null ? fallback : result;
    }

    private void inTxNoResult(TxRunnable action) {
        requiresNewTx.executeWithoutResult(status -> action.run());
    }

    @FunctionalInterface
    interface TxSupplier<T> {
        T get();
    }

    @FunctionalInterface
    interface TxRunnable {
        void run();
    }

    void processClaimedItem(UUID itemId) {
        UploadItem item = itemRepository.findById(itemId).orElse(null);
        if (item == null) {
            return;
        }
        if (item.getStatus() != UploadItemStatus.PROCESSING) {
            return;
        }
        String leaseJobKey = normalizeJobKey(item.getQueueJobKey());
        if (leaseJobKey == null) {
            return;
        }
        UploadBatch batch = item.getBatch();
        UUID batchId = batch.getId();
        UUID jdId = batch.getJobDescription() == null ? null : batch.getJobDescription().getId();
        String fileName = item.getFileName();

        String errorMessage = null;
        UUID candidateId = null;
        UploadItemStatus finalStatus;
        String finalStoragePath = item.getStoragePath();
        try {
            Path source = resolveStoragePath(item.getStoragePath());
            if (source == null || !Files.exists(source)) {
                throw new IllegalStateException("Retry source file missing");
            }

            long fileSize = Files.size(source);
            String contentType = guessContentType(item.getFileName());
            ResumeProcessService.ProcessResult result;
            try (InputStream input = Files.newInputStream(source)) {
                result = resumeProcessService.process(
                        input,
                        contentType,
                        item.getFileName(),
                        fileSize,
                        jdId);
            }

            Path canonical = reconcileStorage(source, result.fileHash());
            if (canonical != null) {
                finalStoragePath = canonical.toString();
            }
            candidateId = result.candidateId();
            finalStatus = result.duplicated() ? UploadItemStatus.DUPLICATE : UploadItemStatus.DONE;
            int updated = itemRepository.completeProcessingSuccess(
                    itemId,
                    leaseJobKey,
                    finalStatus,
                    candidateId,
                    finalStoragePath,
                    UploadItemStatus.PROCESSING);
            if (updated == 0) {
                log.info("Skip stale success commit for itemId={}, jobKey={}", itemId, leaseJobKey);
                return;
            }
        } catch (Exception e) {
            errorMessage = e.getMessage();
            finalStatus = UploadItemStatus.FAILED;
            int updated = itemRepository.completeProcessingFailure(
                    itemId,
                    leaseJobKey,
                    errorMessage,
                    UploadItemStatus.FAILED,
                    UploadItemStatus.PROCESSING);
            if (updated == 0) {
                log.info("Skip stale failure commit for itemId={}, jobKey={}", itemId, leaseJobKey);
                return;
            }
            log.warn("Failed to process queued upload item: itemId={}", itemId, e);
        }

        BatchProgress progress = refreshBatchProgress(batchId);
        batchStreamService.publish(batchId, new BatchStreamEvent(
                batchId,
                finalStatus.name(),
                progress.totalFiles(),
                progress.processedFiles(),
                fileName,
                candidateId,
                errorMessage,
                LocalDateTime.now()
        ));
    }

    private void repairQueuedItemsWithoutJobKey() {
        List<UploadItem> missingJobKeyItems =
                itemRepository.findTop50ByStatusAndQueueJobKeyIsNullAndStoragePathIsNotNullOrderByUpdatedAtAsc(
                        UploadItemStatus.QUEUED);
        for (UploadItem item : missingJobKeyItems) {
            ensureQueueJobKey(item.getId(), item.getQueueJobKey());
        }
    }

    private String ensureQueueJobKey(UUID itemId, String currentJobKey) {
        String normalized = normalizeJobKey(currentJobKey);
        if (normalized != null) {
            return normalized;
        }
        String generated = UploadQueueJobKeyGenerator.nextKey(itemId);
        boolean assigned = inTx(() -> itemRepository.assignQueueJobKeyIfMissing(
                itemId,
                generated,
                UploadItemStatus.QUEUED) > 0, false);
        if (assigned) {
            return generated;
        }
        return itemRepository.findById(itemId)
                .map(UploadItem::getQueueJobKey)
                .map(UploadQueueWorkerService::normalizeJobKey)
                .orElse(null);
    }

    BatchProgress refreshBatchProgress(UUID batchId) {
        batchRepository.refreshProgressFromItems(batchId);
        UploadBatch batch = batchRepository.findById(batchId).orElse(null);
        if (batch == null) {
            return new BatchProgress(0, 0);
        }
        int totalFiles = batch.getTotalFiles() == null ? 0 : Math.max(0, batch.getTotalFiles());
        int processedFiles = batch.getProcessedFiles() == null ? 0 : Math.max(0, batch.getProcessedFiles());
        return new BatchProgress(totalFiles, processedFiles);
    }

    private String guessContentType(String fileName) {
        if (fileName == null) {
            return "application/octet-stream";
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lower.endsWith(".doc")) {
            return "application/msword";
        }
        if (lower.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        return "application/octet-stream";
    }

    private Path resolveStoragePath(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            return null;
        }
        Path resolved = Paths.get(storagePath).toAbsolutePath().normalize();
        if (!resolved.startsWith(storageDir)) {
            log.warn("Reject storage path outside storage-dir: {}", resolved);
            return null;
        }
        return resolved;
    }

    private Path reconcileStorage(Path source, String fileHash) {
        if (source == null || fileHash == null || fileHash.isBlank()) {
            return source;
        }
        Path canonical = storageDir.resolve(fileHash);
        if (source.equals(canonical)) {
            return canonical;
        }
        try {
            if (Files.exists(canonical)) {
                Files.deleteIfExists(source);
                return canonical;
            }
            Files.move(source, canonical, StandardCopyOption.REPLACE_EXISTING);
            return canonical;
        } catch (Exception e) {
            log.warn("Failed to reconcile queued storage file from {} to {}", source, canonical, e);
            return source;
        }
    }

    private static String normalizeJobKey(String jobKey) {
        if (jobKey == null) {
            return null;
        }
        String normalized = jobKey.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static Path resolveStorageDir(String rawDir) {
        String normalized = Objects.requireNonNullElse(rawDir, "").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("app.upload.storage-dir must not be blank");
        }
        return Paths.get(normalized).toAbsolutePath().normalize();
    }

    record BatchProgress(int totalFiles, int processedFiles) {
    }
}
