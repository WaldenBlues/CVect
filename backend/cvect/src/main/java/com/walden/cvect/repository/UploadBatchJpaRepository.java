package com.walden.cvect.repository;

import com.walden.cvect.model.entity.UploadBatch;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UploadBatchJpaRepository extends JpaRepository<UploadBatch, UUID> {

    long countByJobDescriptionId(UUID jobDescriptionId);

    long deleteByJobDescriptionId(UUID jobDescriptionId);

    @Query("select b.id from UploadBatch b where b.jobDescription.id = :jobDescriptionId")
    List<UUID> findIdsByJobDescriptionId(@Param("jobDescriptionId") UUID jobDescriptionId);
}
