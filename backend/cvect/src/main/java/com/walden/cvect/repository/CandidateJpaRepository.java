package com.walden.cvect.repository;

import com.walden.cvect.model.entity.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.Optional;

/**
 * 候选人基础信息数据访问
 */
@Repository
public interface CandidateJpaRepository extends JpaRepository<Candidate, UUID> {
    Optional<Candidate> findByFileHash(String fileHash);

    long countByJobDescriptionId(UUID jobDescriptionId);

    java.util.List<Candidate> findByJobDescriptionIdOrderByCreatedAtDesc(UUID jobDescriptionId);
}
