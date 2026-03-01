package com.walden.cvect.repository;

import com.walden.cvect.model.entity.UploadItem;
import com.walden.cvect.model.entity.UploadItemStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UploadItemJpaRepository extends JpaRepository<UploadItem, UUID> {

    Page<UploadItem> findByBatch_Id(UUID batchId, Pageable pageable);

    Page<UploadItem> findByBatch_IdAndStatus(UUID batchId, UploadItemStatus status, Pageable pageable);

    List<UploadItem> findTop20ByStatusAndStoragePathIsNotNullOrderByUpdatedAtAsc(UploadItemStatus status);

    List<UploadItem> findTop20ByStatusOrderByUpdatedAtAsc(UploadItemStatus status);

    List<UploadItem> findByStatusAndStoragePathIsNotNullOrderByUpdatedAtAsc(
            UploadItemStatus status,
            Pageable pageable);

    List<UploadItem> findTop50ByStatusAndQueueJobKeyIsNullAndStoragePathIsNotNullOrderByUpdatedAtAsc(UploadItemStatus status);

    List<UploadItem> findTop50ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(UploadItemStatus status, LocalDateTime updatedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            DELETE FROM upload_items i
            USING upload_batches b
            WHERE i.batch_id = b.id
              AND b.jd_id = :jobDescriptionId
            """, nativeQuery = true)
    int deleteByJobDescriptionId(@Param("jobDescriptionId") UUID jobDescriptionId);

    Optional<UploadItem> findFirstByBatch_IdAndStatusOrderByUpdatedAtDesc(UUID batchId, UploadItemStatus status);

    long countByBatch_Id(UUID batchId);

    long countByBatch_IdAndStatusIn(UUID batchId, Collection<UploadItemStatus> statuses);

    long countByStatusIn(Collection<UploadItemStatus> statuses);

    @Query("""
            select i.status as status, count(i) as count
            from UploadItem i
            where i.batch.id = :batchId
            group by i.status
            """)
    List<UploadItemStatusCount> countGroupedByStatus(@Param("batchId") UUID batchId);

    @Query("""
            select i.id
            from UploadItem i
            where i.batch.id = :batchId
              and i.status = :failedStatus
              and i.storagePath is not null
            """)
    List<UUID> findRetryableFailedItemIds(@Param("batchId") UUID batchId, @Param("failedStatus") UploadItemStatus failedStatus);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update UploadItem i
            set i.status = :queuedStatus,
                i.queueJobKey = :jobKey,
                i.attempt = coalesce(i.attempt, 0) + 1,
                i.updatedAt = current_timestamp
            where i.id = :itemId
              and i.status = :failedStatus
              and i.storagePath is not null
            """)
    int markFailedAsQueuedById(
            @Param("itemId") UUID itemId,
            @Param("jobKey") String jobKey,
            @Param("queuedStatus") UploadItemStatus queuedStatus,
            @Param("failedStatus") UploadItemStatus failedStatus);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            WITH picked AS (
              SELECT id
              FROM upload_items
              WHERE status = 'QUEUED'
                AND storage_path IS NOT NULL
                AND queue_job_key IS NOT NULL
              ORDER BY updated_at ASC
              FOR UPDATE SKIP LOCKED
              LIMIT :batchSize
            )
            UPDATE upload_items i
            SET status = 'PROCESSING',
                started_at = current_timestamp,
                updated_at = current_timestamp
            FROM picked
            WHERE i.id = picked.id
            RETURNING i.id
            """, nativeQuery = true)
    List<UUID> claimNextQueuedBatch(@Param("batchSize") int batchSize);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update UploadItem i
            set i.queueJobKey = :jobKey,
                i.updatedAt = current_timestamp
            where i.id = :itemId
              and i.status = :queuedStatus
              and i.storagePath is not null
              and i.queueJobKey is null
            """)
    int assignQueueJobKeyIfMissing(
            @Param("itemId") UUID itemId,
            @Param("jobKey") String jobKey,
            @Param("queuedStatus") UploadItemStatus queuedStatus);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update UploadItem i
            set i.status = :queuedStatus,
                i.queueJobKey = :newJobKey,
                i.errorMessage = :message,
                i.updatedAt = current_timestamp
            where i.id = :itemId
              and i.status = :processingStatus
              and ((:oldJobKey is null and i.queueJobKey is null) or i.queueJobKey = :oldJobKey)
            """)
    int recoverStaleProcessingLease(
            @Param("itemId") UUID itemId,
            @Param("oldJobKey") String oldJobKey,
            @Param("newJobKey") String newJobKey,
            @Param("message") String message,
            @Param("queuedStatus") UploadItemStatus queuedStatus,
            @Param("processingStatus") UploadItemStatus processingStatus);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update UploadItem i
            set i.status = :finalStatus,
                i.candidateId = :candidateId,
                i.storagePath = :storagePath,
                i.errorMessage = null,
                i.queueJobKey = null,
                i.updatedAt = current_timestamp
            where i.id = :itemId
              and i.status = :processingStatus
              and i.queueJobKey = :jobKey
            """)
    int completeProcessingSuccess(
            @Param("itemId") UUID itemId,
            @Param("jobKey") String jobKey,
            @Param("finalStatus") UploadItemStatus finalStatus,
            @Param("candidateId") UUID candidateId,
            @Param("storagePath") String storagePath,
            @Param("processingStatus") UploadItemStatus processingStatus);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update UploadItem i
            set i.status = :failedStatus,
                i.errorMessage = :errorMessage,
                i.queueJobKey = null,
                i.updatedAt = current_timestamp
            where i.id = :itemId
              and i.status = :processingStatus
              and i.queueJobKey = :jobKey
            """)
    int completeProcessingFailure(
            @Param("itemId") UUID itemId,
            @Param("jobKey") String jobKey,
            @Param("errorMessage") String errorMessage,
            @Param("failedStatus") UploadItemStatus failedStatus,
            @Param("processingStatus") UploadItemStatus processingStatus);

    interface UploadItemStatusCount {
        UploadItemStatus getStatus();

        long getCount();
    }
}
