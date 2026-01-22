package com.walden.cvect.repository;

import com.walden.cvect.model.entity.Experience;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExperienceJpaRepository extends JpaRepository<Experience, UUID> {

    List<Experience> findByCandidateId(UUID candidateId);
}
