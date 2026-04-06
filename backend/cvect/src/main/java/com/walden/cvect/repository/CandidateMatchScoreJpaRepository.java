package com.walden.cvect.repository;

import com.walden.cvect.model.entity.CandidateMatchScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CandidateMatchScoreJpaRepository extends JpaRepository<CandidateMatchScore, UUID> {

    Optional<CandidateMatchScore> findByCandidateIdAndJobDescriptionId(UUID candidateId, UUID jobDescriptionId);

    List<CandidateMatchScore> findByJobDescriptionIdAndCandidateIdIn(UUID jobDescriptionId, Collection<UUID> candidateIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from CandidateMatchScore c where c.jobDescriptionId = :jobDescriptionId")
    int deleteByJobDescriptionId(@Param("jobDescriptionId") UUID jobDescriptionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from CandidateMatchScore c where c.candidateId = :candidateId")
    int deleteByCandidateId(@Param("candidateId") UUID candidateId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from CandidateMatchScore c where c.candidateId in :candidateIds")
    int deleteByCandidateIds(@Param("candidateIds") Collection<UUID> candidateIds);
}
