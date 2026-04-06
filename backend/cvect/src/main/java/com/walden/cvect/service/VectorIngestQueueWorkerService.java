package com.walden.cvect.service;

import com.walden.cvect.infra.vector.VectorStoreService;
import com.walden.cvect.model.entity.vector.VectorIngestTask;
import com.walden.cvect.model.entity.vector.VectorIngestTaskStatus;
import com.walden.cvect.repository.VectorIngestTaskJpaRepository;
import com.walden.cvect.web.stream.CandidateStreamEvent;
import com.walden.cvect.web.stream.CandidateStreamService;
import com.walden.cvect.web.stream.VectorStatusStreamEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Service
@ConditionalOnProperty(name = "app.vector.ingest.worker.enabled", havingValue = "true", matchIfMissing = true)
public class VectorIngestQueueWorkerService {

    private static final Logger log = LoggerFactory.getLogger(VectorIngestQueueWorkerService.class);

    private final VectorIngestTaskJpaRepository taskRepository;
    private final VectorStoreService vectorStoreService;
    private final CandidateSnapshotService snapshotService;
    private final CandidateStreamService streamService;
    private final PersistedMatchScoreService persistedMatchScoreService;
    private final TransactionTemplate requiresNewTx;
    private final int claimBatchSize;
    private final int maxAttempts;
    private final Duration staleProcessingTimeout;
    private final long maintenanceIntervalMs;
    private final AtomicBoolean useNativeClaimQuery;
    private final AtomicLong lastMaintenanceAtMs = new AtomicLong(0L);

    public VectorIngestQueueWorkerService(
            VectorIngestTaskJpaRepository taskRepository,
            VectorStoreService vectorStoreService,
            CandidateSnapshotService snapshotService,
            CandidateStreamService streamService,
            PersistedMatchScoreService persistedMatchScoreService,
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            @Value("${app.vector.ingest.worker.claim-batch-size:20}") int claimBatchSize,
            @Value("${app.vector.ingest.worker.max-attempts:3}") int maxAttempts,
            @Value("${app.vector.ingest.worker.stale-processing-ms:300000}") long staleProcessingMs,
            @Value("${app.vector.ingest.worker.maintenance-interval-ms:5000}") long maintenanceIntervalMs) {
        this.taskRepository = taskRepository;
        this.vectorStoreService = vectorStoreService;
        this.snapshotService = snapshotService;
        this.streamService = streamService;
        this.persistedMatchScoreService = persistedMatchScoreService;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.claimBatchSize = Math.max(1, Math.min(claimBatchSize, 200));
        this.maxAttempts = Math.max(1, maxAttempts);
        this.staleProcessingTimeout = Duration.ofMillis(Math.max(0L, staleProcessingMs));
        this.maintenanceIntervalMs = Math.max(1000L, maintenanceIntervalMs);
        this.useNativeClaimQuery = new AtomicBoolean(isPostgreSql(jdbcTemplate));
    }

    public int consumePendingTasks() {
        runMaintenanceIfDue();

        List<UUID> claimedTaskIds = claimNextPendingBatchWithFallback();
        if (claimedTaskIds.isEmpty()) {
            return 0;
        }
        for (UUID taskId : claimedTaskIds) {
            processClaimedTask(taskId);
        }
        return claimedTaskIds.size();
    }

    private List<UUID> claimNextPendingBatchWithFallback() {
        if (!useNativeClaimQuery.get()) {
            return claimNextPendingBatchPortable();
        }
        try {
            return inTx(() -> taskRepository.claimNextPendingBatch(claimBatchSize), List.of());
        } catch (RuntimeException ex) {
            useNativeClaimQuery.set(false);
            log.warn("Disable native vector claim query after failure; fallback to portable claim strategy", ex);
            return claimNextPendingBatchPortable();
        }
    }

    private List<UUID> claimNextPendingBatchPortable() {
        return inTx(() -> {
            List<VectorIngestTask> pendingTasks = taskRepository.findByStatusOrderByUpdatedAtAsc(
                    VectorIngestTaskStatus.PENDING,
                    PageRequest.of(0, claimBatchSize));
            if (pendingTasks.isEmpty()) {
                return List.of();
            }
            List<UUID> claimedIds = new ArrayList<>();
            for (VectorIngestTask task : pendingTasks) {
                int updated = taskRepository.claimPendingTaskById(
                        task.getId(),
                        VectorIngestTaskStatus.PROCESSING,
                        VectorIngestTaskStatus.PENDING);
                if (updated > 0) {
                    claimedIds.add(task.getId());
                }
            }
            return claimedIds;
        }, List.of());
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
        recoverStaleProcessingTasks();
    }

    private void recoverStaleProcessingTasks() {
        LocalDateTime threshold = LocalDateTime.now().minus(staleProcessingTimeout);
        while (true) {
            List<VectorIngestTask> staleTasks = taskRepository.findTop50ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                    VectorIngestTaskStatus.PROCESSING,
                    threshold);
            if (staleTasks.isEmpty()) {
                return;
            }
            int recovered = 0;
            for (VectorIngestTask task : staleTasks) {
                int updated = inTx(() -> taskRepository.recoverStaleProcessing(
                        task.getId(),
                        "Recovered stale vector ingest lease",
                        VectorIngestTaskStatus.PENDING,
                        VectorIngestTaskStatus.PROCESSING), 0);
                if (updated > 0) {
                    recovered++;
                }
            }
            if (recovered == 0) {
                return;
            }
            log.info("Recovered {} stale vector ingest tasks", recovered);
        }
    }

    private void processClaimedTask(UUID taskId) {
        VectorIngestTask task = inTx(() -> taskRepository.findById(taskId).orElse(null), null);
        if (task == null || task.getStatus() != VectorIngestTaskStatus.PROCESSING) {
            return;
        }
        try {
            // Keep vector write and final state transition in separate transactions.
            // If vectorStoreService.save throws, the write tx rolls back without poisoning the status update tx.
            boolean persisted = inTx(() -> vectorStoreService.save(
                    task.getCandidateId(),
                    task.getChunkType(),
                    task.getContent()), false);
            if (!persisted) {
                throw new IllegalStateException("Vector chunk was not persisted");
            }
            int updated = inTx(() -> taskRepository.completeSuccess(
                    taskId,
                    VectorIngestTaskStatus.DONE,
                    VectorIngestTaskStatus.PROCESSING), 0);
            if (updated == 0) {
                log.info("Skip stale vector success commit for taskId={}", taskId);
                return;
            }
            publishVectorDoneIfReady(task.getCandidateId());
        } catch (Exception ex) {
            boolean transientEmbeddingOutage = isTransientEmbeddingOutage(ex);
            int currentAttempt = task.getAttempt() == null ? 0 : task.getAttempt();
            int nextAttempt = currentAttempt + 1;
            VectorIngestTaskStatus nextStatus;
            if (nextAttempt >= maxAttempts) {
                nextStatus = VectorIngestTaskStatus.FAILED;
            } else {
                // Keep task retryable for transient outages, but still cap attempts.
                nextStatus = VectorIngestTaskStatus.PENDING;
            }
            int updated = inTx(() -> taskRepository.completeFailure(
                    taskId,
                    nextAttempt,
                    ex.getMessage(),
                    nextStatus,
                    VectorIngestTaskStatus.PROCESSING), 0);
            if (updated == 0) {
                log.info("Skip stale vector failure commit for taskId={}", taskId);
                return;
            }
            if (transientEmbeddingOutage) {
                log.warn("Vector ingest task delayed due to embedding connectivity issue: taskId={}", taskId, ex);
            } else if (nextStatus == VectorIngestTaskStatus.FAILED) {
                log.warn("Vector ingest task permanently failed: taskId={}, attempts={}", taskId, nextAttempt, ex);
            } else {
                log.warn("Vector ingest task failed and will retry: taskId={}, attempt={}", taskId, nextAttempt, ex);
            }
        }
    }

    private void publishVectorDoneIfReady(UUID candidateId) {
        if (candidateId == null) {
            return;
        }

        boolean hasInflight = inTx(() -> taskRepository.existsByCandidateIdAndStatusIn(
                candidateId,
                List.of(VectorIngestTaskStatus.PENDING, VectorIngestTaskStatus.PROCESSING)), false);
        if (hasInflight) {
            return;
        }
        boolean hasDone = inTx(() -> taskRepository.existsByCandidateIdAndStatus(
                candidateId,
                VectorIngestTaskStatus.DONE), false);
        if (!hasDone) {
            return;
        }

        persistedMatchScoreService.refreshForCandidate(candidateId);

        CandidateStreamEvent snapshot = snapshotService.build(candidateId, "VECTOR_DONE");
        if (snapshot == null) {
            return;
        }

        streamService.publishVectorStatus(new VectorStatusStreamEvent(
                snapshot.candidateId(),
                snapshot.jdId(),
                "VECTOR_DONE",
                false,
                LocalDateTime.now()));
    }

    private static boolean isTransientEmbeddingOutage(Throwable ex) {
        Throwable cursor = ex;
        while (cursor != null) {
            String message = cursor.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("connection refused")
                        || normalized.contains("connectexception")
                        || normalized.contains("timed out")
                        || normalized.contains("timeout")) {
                    return true;
                }
            }
            cursor = cursor.getCause();
        }
        return false;
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

    private static boolean isPostgreSql(JdbcTemplate jdbcTemplate) {
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            String productName = connection.getMetaData().getDatabaseProductName();
            return productName != null && productName.toLowerCase(Locale.ROOT).contains("postgresql");
        } catch (Exception ex) {
            return false;
        }
    }
}
