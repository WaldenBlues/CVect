package com.walden.cvect.service;

import com.walden.cvect.model.entity.UploadBatch;
import com.walden.cvect.model.entity.UploadBatchStatus;
import com.walden.cvect.model.entity.UploadItem;
import com.walden.cvect.model.entity.UploadItemStatus;
import com.walden.cvect.repository.UploadBatchJpaRepository;
import com.walden.cvect.repository.UploadItemJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class UploadBatchService {

    private static final Logger log = LoggerFactory.getLogger(UploadBatchService.class);
    private static final String LEGACY_PENDING = "PENDING";
    private static final String LEGACY_RETRYING = "RETRYING";
    private static final String LEGACY_SUCCEEDED = "SUCCEEDED";

    private final UploadBatchJpaRepository batchRepository;
    private final UploadItemJpaRepository itemRepository;
    private final MeterRegistry meterRegistry;

    public UploadBatchService(UploadBatchJpaRepository batchRepository,
            UploadItemJpaRepository itemRepository,
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.batchRepository = batchRepository;
        this.itemRepository = itemRepository;
        this.meterRegistry = meterRegistryProvider.getIfAvailable();
    }

    @Transactional(readOnly = true)
    public Optional<BatchOverview> getBatchOverview(UUID batchId) {
        UploadBatch batch = batchRepository.findById(batchId).orElse(null);
        if (batch == null) {
            return Optional.empty();
        }

        Map<UploadItemStatus, Long> countsByStatus = new EnumMap<>(UploadItemStatus.class);
        for (UploadItemJpaRepository.UploadItemStatusCount count : itemRepository.countGroupedByStatus(batchId)) {
            countsByStatus.put(count.getStatus(), count.getCount());
        }

        long total = countsByStatus.values().stream().mapToLong(Long::longValue).sum();
        long succeeded = get(countsByStatus, UploadItemStatus.DONE)
                + get(countsByStatus, UploadItemStatus.DUPLICATE);
        long failed = get(countsByStatus, UploadItemStatus.FAILED);
        long processing = get(countsByStatus, UploadItemStatus.PROCESSING);
        long pending = get(countsByStatus, UploadItemStatus.QUEUED);

        String lastError = itemRepository.findFirstByBatch_IdAndStatusOrderByUpdatedAtDesc(batchId, UploadItemStatus.FAILED)
                .map(UploadItem::getErrorMessage)
                .orElse(null);

        UUID jdId = batch.getJobDescription() == null ? null : batch.getJobDescription().getId();
        BatchCounts counts = new BatchCounts(total, succeeded, failed, processing, pending);
        return Optional.of(new BatchOverview(
                batch.getId(),
                jdId,
                batch.getCreatedAt(),
                batch.getUpdatedAt(),
                batch.getStatus() == null ? null : batch.getStatus().name(),
                counts,
                lastError));
    }

    @Transactional(readOnly = true)
    public Optional<Page<UploadItemView>> getBatchItems(UUID batchId, String status, Pageable pageable) {
        if (!batchRepository.existsById(batchId)) {
            return Optional.empty();
        }
        Page<UploadItem> page;
        if (status == null || status.isBlank()) {
            page = itemRepository.findByBatch_Id(batchId, pageable);
        } else {
            recordLegacyStatusUsage(status);
            UploadItemStatus parsedStatus = UploadItemStatus.parseOrNull(status);
            if (parsedStatus == null) {
                return Optional.of(Page.empty(pageable));
            }
            page = itemRepository.findByBatch_IdAndStatus(batchId, parsedStatus, pageable);
        }
        return Optional.of(page.map(item -> new UploadItemView(
                item.getId(),
                batchId,
                item.getFileName(),
                item.getStatus() == null ? null : item.getStatus().name(),
                item.getAttempt() == null ? 0 : item.getAttempt(),
                item.getErrorMessage(),
                item.getCreatedAt(),
                item.getUpdatedAt())));
    }

    @Transactional
    public Optional<RetryFailedResult> retryFailed(UUID batchId) {
        UploadBatch batch = batchRepository.findById(batchId).orElse(null);
        if (batch == null) {
            return Optional.empty();
        }

        int retriedCount = 0;
        for (UUID itemId : itemRepository.findRetryableFailedItemIds(batchId, UploadItemStatus.FAILED)) {
            String jobKey = UploadQueueJobKeyGenerator.nextKey(itemId);
            retriedCount += itemRepository.markFailedAsQueuedById(
                    itemId,
                    jobKey,
                    UploadItemStatus.QUEUED,
                    UploadItemStatus.FAILED);
        }
        if (retriedCount > 0) {
            batch.setStatus(UploadBatchStatus.PROCESSING);
            batchRepository.save(batch);
        }
        return Optional.of(new RetryFailedResult(batchId, retriedCount));
    }

    private static long get(Map<UploadItemStatus, Long> counts, UploadItemStatus status) {
        return counts.getOrDefault(status, 0L);
    }

    private void recordLegacyStatusUsage(String raw) {
        if (raw == null) {
            return;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals(LEGACY_PENDING)
                && !normalized.equals(LEGACY_RETRYING)
                && !normalized.equals(LEGACY_SUCCEEDED)) {
            return;
        }
        if (meterRegistry != null) {
            meterRegistry.counter("cvect.compat.upload_status_legacy", "legacy_status", normalized).increment();
        }
        log.info("Legacy upload status filter received: {}", normalized);
    }

    public record BatchOverview(
            UUID id,
            UUID jdId,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            String status,
            BatchCounts counts,
            String lastError) {
    }

    public record BatchCounts(
            long total,
            long succeeded,
            long failed,
            long processing,
            long pending) {
    }

    public record UploadItemView(
            UUID id,
            UUID batchId,
            String fileName,
            String status,
            int attempt,
            String errorMessage,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record RetryFailedResult(UUID batchId, int retriedCount) {
    }
}
