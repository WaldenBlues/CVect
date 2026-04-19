package com.walden.cvect.repository;

import com.walden.cvect.model.entity.UploadBatch;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public interface UploadBatchJpaRepository extends JpaRepository<UploadBatch, UUID> {

    long countByJobDescriptionId(UUID jobDescriptionId);

    long countByTenantIdAndJobDescriptionId(UUID tenantId, UUID jobDescriptionId);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    java.util.Optional<UploadBatch> findByIdAndTenantId(UUID id, UUID tenantId);

    long deleteByJobDescriptionId(UUID jobDescriptionId);

    long deleteByTenantIdAndJobDescriptionId(UUID tenantId, UUID jobDescriptionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = """
            UPDATE upload_batches b
            SET processed_files = agg.processed_files,
                total_files = CASE
                  WHEN b.total_files IS NULL OR b.total_files <= 0 THEN agg.total_files
                  ELSE b.total_files
                END,
                status = CASE
                  WHEN agg.processing_count = 0
                   AND agg.queued_count = 0
                   AND agg.processed_files >=
                     (CASE
                        WHEN b.total_files IS NULL OR b.total_files <= 0 THEN agg.total_files
                        ELSE b.total_files
                      END)
                  THEN 'DONE'
                  ELSE 'PROCESSING'
                END,
                updated_at = current_timestamp
            FROM (
                SELECT
                    CAST(:batchId AS uuid) AS batch_id,
                    count(*) AS total_files,
                    count(*) FILTER (WHERE status IN ('DONE', 'DUPLICATE', 'FAILED')) AS processed_files,
                    count(*) FILTER (WHERE status = 'PROCESSING') AS processing_count,
                    count(*) FILTER (WHERE status = 'QUEUED') AS queued_count
                FROM upload_items
                WHERE batch_id = :batchId
            ) agg
            WHERE b.id = agg.batch_id
            """, nativeQuery = true)
    int refreshProgressFromItems(@Param("batchId") UUID batchId);
}
