package com.walden.cvect.service.vector;

import com.walden.cvect.model.ChunkType;
import com.walden.cvect.model.entity.vector.VectorIngestTask;
import com.walden.cvect.model.entity.vector.VectorIngestTaskStatus;
import com.walden.cvect.repository.VectorIngestTaskJpaRepository;
import com.walden.cvect.infra.vector.VectorStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class VectorIngestService {

    private static final Logger log = LoggerFactory.getLogger(VectorIngestService.class);

    private final VectorIngestTaskJpaRepository taskRepository;
    private final VectorStoreService vectorStoreService;
    private final boolean vectorEnabled;
    private final boolean workerEnabled;
    private final long maxPendingItems;

    public VectorIngestService(
            VectorIngestTaskJpaRepository taskRepository,
            VectorStoreService vectorStoreService,
            @Value("${app.vector.enabled:true}") boolean vectorEnabled,
            @Value("${app.vector.ingest.worker.enabled:true}") boolean workerEnabled,
            @Value("${app.vector.ingest.max-pending-items:5000}") long maxPendingItems) {
        this.taskRepository = taskRepository;
        this.vectorStoreService = vectorStoreService;
        this.vectorEnabled = vectorEnabled;
        this.workerEnabled = workerEnabled;
        this.maxPendingItems = Math.max(1L, maxPendingItems);
    }

    public void ingest(UUID candidateId, ChunkType chunkType, String content) {
        if (candidateId == null || chunkType == null || content == null || content.isBlank()) {
            return;
        }
        if (!vectorEnabled || !workerEnabled) {
            return;
        }
        long inflight = taskRepository.countByStatusIn(List.of(
                VectorIngestTaskStatus.PENDING,
                VectorIngestTaskStatus.PROCESSING));
        if (inflight >= maxPendingItems) {
            log.warn("Vector ingest queue is full, using inline fallback: candidateId={}, chunkType={}, inflight={}, limit={}",
                    candidateId, chunkType, inflight, maxPendingItems);
            persistInline(candidateId, chunkType, content, null);
            return;
        }
        try {
            taskRepository.save(new VectorIngestTask(candidateId, chunkType, content));
        } catch (Exception ex) {
            log.warn("Failed to enqueue vector ingest task, using inline fallback: candidateId={}, chunkType={}",
                    candidateId, chunkType, ex);
            persistInline(candidateId, chunkType, content, ex);
        }
    }

    private void persistInline(UUID candidateId, ChunkType chunkType, String content, Exception enqueueFailure) {
        boolean saved = vectorStoreService.save(candidateId, chunkType, content);
        if (!saved) {
            String message = "Failed to persist vector chunk inline for candidateId=" + candidateId
                    + ", chunkType=" + chunkType;
            if (enqueueFailure == null) {
                throw new IllegalStateException(message);
            }
            throw new IllegalStateException(message, enqueueFailure);
        }
        log.info("Persisted vector chunk inline: candidateId={}, chunkType={}", candidateId, chunkType);
    }
}
