package com.walden.cvect.repository;

import com.walden.cvect.model.entity.Honor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 荣誉数据访问
 */
@Repository
public interface HonorJpaRepository extends JpaRepository<Honor, UUID> {

    List<Honor> findByCandidateId(UUID candidateId);
}
