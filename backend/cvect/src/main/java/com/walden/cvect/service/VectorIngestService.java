package com.walden.cvect.service;

import com.walden.cvect.model.ChunkType;
import com.walden.cvect.model.entity.vector.VectorIngestTask;
import com.walden.cvect.model.entity.vector.VectorIngestTaskStatus;
import com.walden.cvect.repository.VectorIngestTaskJpaRepository;
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
    private final long maxPendingItems;

    public VectorIngestService(
            VectorIngestTaskJpaRepository taskRepository,
            @Value("${app.vector.ingest.max-pending-items:5000}") long maxPendingItems) {
        this.taskRepository = taskRepository;
        this.maxPendingItems = Math.max(1L, maxPendingItems);
    }

    public void ingest(UUID candidateId, ChunkType chunkType, String content) {
        if (candidateId == null || chunkType == null || content == null || content.isBlank()) {
            return;
        }
        long inflight = taskRepository.countByStatusIn(List.of(
                VectorIngestTaskStatus.PENDING,
                VectorIngestTaskStatus.PROCESSING));
        if (inflight >= maxPendingItems) {
            log.warn("Skip vector ingest enqueue because queue is full: candidateId={}, chunkType={}, inflight={}, limit={}",
                    candidateId, chunkType, inflight, maxPendingItems);
            return;
        }
        try {
            taskRepository.save(new VectorIngestTask(candidateId, chunkType, content));
        } catch (Exception ex) {
            log.warn("Failed to enqueue vector ingest task: candidateId={}, chunkType={}",
                    candidateId, chunkType, ex);
        }
    }
}
