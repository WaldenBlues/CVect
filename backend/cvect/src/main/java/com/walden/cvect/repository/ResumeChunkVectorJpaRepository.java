package com.walden.cvect.repository;

import com.walden.cvect.model.entity.vector.ResumeChunkVector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ResumeChunkVectorJpaRepository extends JpaRepository<ResumeChunkVector, UUID> {

    @Query("select distinct r.candidateId from ResumeChunkVector r where r.candidateId in :candidateIds")
    List<UUID> findDistinctCandidateIdsIn(Collection<UUID> candidateIds);
}

