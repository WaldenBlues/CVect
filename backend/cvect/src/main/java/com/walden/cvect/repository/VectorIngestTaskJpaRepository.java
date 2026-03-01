package com.walden.cvect.repository;

import com.walden.cvect.model.entity.vector.VectorIngestTask;
import com.walden.cvect.model.entity.vector.VectorIngestTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface VectorIngestTaskJpaRepository extends JpaRepository<VectorIngestTask, UUID> {

    long countByStatusIn(Collection<VectorIngestTaskStatus> statuses);

    boolean existsByCandidateIdAndStatus(UUID candidateId, VectorIngestTaskStatus status);

    boolean existsByCandidateIdAndStatusIn(UUID candidateId, Collection<VectorIngestTaskStatus> statuses);

    List<VectorIngestTask> findTop50ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            VectorIngestTaskStatus status,
            LocalDateTime updatedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            WITH picked AS (
              SELECT id
              FROM vector_ingest_tasks
              WHERE status = 'PENDING'
              ORDER BY updated_at ASC
              FOR UPDATE SKIP LOCKED
              LIMIT :batchSize
            )
            UPDATE vector_ingest_tasks t
            SET status = 'PROCESSING',
                started_at = current_timestamp,
                updated_at = current_timestamp
            FROM picked
            WHERE t.id = picked.id
            RETURNING t.id
            """, nativeQuery = true)
    List<UUID> claimNextPendingBatch(@Param("batchSize") int batchSize);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update VectorIngestTask t
            set t.status = :toStatus,
                t.errorMessage = :message,
                t.startedAt = null,
                t.updatedAt = current_timestamp
            where t.id = :taskId
              and t.status = :fromStatus
            """)
    int recoverStaleProcessing(
            @Param("taskId") UUID taskId,
            @Param("message") String message,
            @Param("toStatus") VectorIngestTaskStatus toStatus,
            @Param("fromStatus") VectorIngestTaskStatus fromStatus);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update VectorIngestTask t
            set t.status = :doneStatus,
                t.errorMessage = null,
                t.updatedAt = current_timestamp
            where t.id = :taskId
              and t.status = :processingStatus
            """)
    int completeSuccess(
            @Param("taskId") UUID taskId,
            @Param("doneStatus") VectorIngestTaskStatus doneStatus,
            @Param("processingStatus") VectorIngestTaskStatus processingStatus);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update VectorIngestTask t
            set t.status = :nextStatus,
                t.attempt = :attempt,
                t.errorMessage = :errorMessage,
                t.startedAt = null,
                t.updatedAt = current_timestamp
            where t.id = :taskId
              and t.status = :processingStatus
            """)
    int completeFailure(
            @Param("taskId") UUID taskId,
            @Param("attempt") int attempt,
            @Param("errorMessage") String errorMessage,
            @Param("nextStatus") VectorIngestTaskStatus nextStatus,
            @Param("processingStatus") VectorIngestTaskStatus processingStatus);
}
