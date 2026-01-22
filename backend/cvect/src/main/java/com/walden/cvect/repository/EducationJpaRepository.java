package com.walden.cvect.repository;

import com.walden.cvect.model.entity.Education;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EducationJpaRepository extends JpaRepository<Education, UUID> {

    List<Education> findByCandidateId(UUID candidateId);
}
