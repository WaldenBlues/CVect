package com.walden.cvect.repository;

import com.walden.cvect.model.entity.JobDescription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobDescriptionJpaRepository extends JpaRepository<JobDescription, UUID> {

    List<JobDescription> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<JobDescription> findByIdAndTenantId(UUID id, UUID tenantId);

    List<JobDescription> findByTenantId(UUID tenantId);
}
