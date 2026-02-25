package com.walden.cvect.repository;

import com.walden.cvect.model.entity.CandidateSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CandidateSnapshotJpaRepository extends JpaRepository<CandidateSnapshot, UUID> {

    List<CandidateSnapshot> findByJdIdOrderByCandidateCreatedAtDesc(UUID jdId);
}
