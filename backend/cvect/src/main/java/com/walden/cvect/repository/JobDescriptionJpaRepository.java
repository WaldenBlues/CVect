package com.walden.cvect.repository;

import com.walden.cvect.model.entity.JobDescription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobDescriptionJpaRepository extends JpaRepository<JobDescription, UUID> {

    List<JobDescription> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<JobDescription> findByTenantIdAndCreatedByUserIdOrderByCreatedAtDesc(UUID tenantId, UUID createdByUserId);

    Optional<JobDescription> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<JobDescription> findByIdAndTenantIdAndCreatedByUserId(UUID id, UUID tenantId, UUID createdByUserId);

    boolean existsByIdAndTenantIdAndCreatedByUserId(UUID id, UUID tenantId, UUID createdByUserId);

    List<JobDescription> findByTenantId(UUID tenantId);

    List<JobDescription> findByTenantIdAndCreatedByUserId(UUID tenantId, UUID createdByUserId);
}
