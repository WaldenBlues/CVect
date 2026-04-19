package com.walden.cvect.repository;

import com.walden.cvect.model.entity.CandidateSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CandidateSnapshotJpaRepository extends JpaRepository<CandidateSnapshot, UUID> {

    List<CandidateSnapshot> findByJdIdOrderByCandidateCreatedAtDesc(UUID jdId);

    List<CandidateSnapshot> findByTenantIdAndJdIdOrderByCandidateCreatedAtDesc(UUID tenantId, UUID jdId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from CandidateSnapshot s where s.jdId = :jobDescriptionId")
    int deleteByJdId(@Param("jobDescriptionId") UUID jobDescriptionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from CandidateSnapshot s where s.tenantId = :tenantId and s.jdId = :jobDescriptionId")
    int deleteByTenantIdAndJdId(
            @Param("tenantId") UUID tenantId,
            @Param("jobDescriptionId") UUID jobDescriptionId);
}
