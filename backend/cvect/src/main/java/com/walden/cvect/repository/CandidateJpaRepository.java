package com.walden.cvect.repository;

import com.walden.cvect.model.entity.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.Optional;
import java.util.List;
import java.util.Collection;

/**
 * 候选人基础信息数据访问
 */
@Repository
public interface CandidateJpaRepository extends JpaRepository<Candidate, UUID> {
    Optional<Candidate> findByFileHashAndJobDescriptionId(String fileHash, UUID jobDescriptionId);

    Optional<Candidate> findByTenantIdAndFileHashAndJobDescriptionId(UUID tenantId, String fileHash, UUID jobDescriptionId);

    Optional<Candidate> findByFileHashAndJobDescriptionIsNull(String fileHash);

    Optional<Candidate> findByTenantIdAndFileHashAndJobDescriptionIsNull(UUID tenantId, String fileHash);

    long countByJobDescriptionId(UUID jobDescriptionId);

    long countByTenantIdAndJobDescriptionId(UUID tenantId, UUID jobDescriptionId);

    List<Candidate> findByJobDescriptionIdOrderByCreatedAtDesc(UUID jobDescriptionId);

    List<Candidate> findByTenantIdAndJobDescriptionIdOrderByCreatedAtDesc(UUID tenantId, UUID jobDescriptionId);

    @Query("""
            select c from Candidate c
            where c.tenantId = :tenantId
              and c.jobDescription.id = :jobDescriptionId
              and c.jobDescription.createdByUserId = :createdByUserId
            order by c.createdAt desc
            """)
    List<Candidate> findByTenantIdAndJobDescriptionIdAndJobDescriptionCreatedByUserIdOrderByCreatedAtDesc(
            @Param("tenantId") UUID tenantId,
            @Param("jobDescriptionId") UUID jobDescriptionId,
            @Param("createdByUserId") UUID createdByUserId);

    Optional<Candidate> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("""
            select c from Candidate c
            where c.id = :id
              and c.tenantId = :tenantId
              and c.jobDescription.createdByUserId = :createdByUserId
            """)
    Optional<Candidate> findByIdAndTenantIdAndJobDescriptionCreatedByUserId(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId,
            @Param("createdByUserId") UUID createdByUserId);

    @Query("select c.id from Candidate c where c.jobDescription.id = :jobDescriptionId")
    List<UUID> findIdsByJobDescriptionId(@Param("jobDescriptionId") UUID jobDescriptionId);

    @Query("select c.id from Candidate c where c.tenantId = :tenantId")
    List<UUID> findIdsByTenantId(@Param("tenantId") UUID tenantId);

    @Query("""
            select c.id from Candidate c
            where c.tenantId = :tenantId
              and c.jobDescription.createdByUserId = :createdByUserId
            """)
    List<UUID> findIdsByTenantIdAndJobDescriptionCreatedByUserId(
            @Param("tenantId") UUID tenantId,
            @Param("createdByUserId") UUID createdByUserId);

    @Query("select c.id from Candidate c where c.tenantId = :tenantId and c.jobDescription.id = :jobDescriptionId")
    List<UUID> findIdsByTenantIdAndJobDescriptionId(
            @Param("tenantId") UUID tenantId,
            @Param("jobDescriptionId") UUID jobDescriptionId);

    @Query("""
            select c.jobDescription.id as jdId, count(c) as count
            from Candidate c
            where c.jobDescription.id in :jobDescriptionIds
            group by c.jobDescription.id
            """)
    List<JobDescriptionCandidateCount> countGroupedByJobDescriptionIds(
            @Param("jobDescriptionIds") Collection<UUID> jobDescriptionIds);

    @Query("""
            select c.jobDescription.id as jdId, count(c) as count
            from Candidate c
            where c.tenantId = :tenantId
              and c.jobDescription.id in :jobDescriptionIds
            group by c.jobDescription.id
            """)
    List<JobDescriptionCandidateCount> countGroupedByTenantIdAndJobDescriptionIds(
            @Param("tenantId") UUID tenantId,
            @Param("jobDescriptionIds") Collection<UUID> jobDescriptionIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Candidate c where c.jobDescription.id = :jobDescriptionId")
    int deleteByJobDescriptionId(@Param("jobDescriptionId") UUID jobDescriptionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Candidate c where c.tenantId = :tenantId and c.jobDescription.id = :jobDescriptionId")
    int deleteByTenantIdAndJobDescriptionId(
            @Param("tenantId") UUID tenantId,
            @Param("jobDescriptionId") UUID jobDescriptionId);

    interface JobDescriptionCandidateCount {
        UUID getJdId();

        long getCount();
    }
}
