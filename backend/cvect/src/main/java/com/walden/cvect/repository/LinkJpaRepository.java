package com.walden.cvect.repository;

import com.walden.cvect.model.entity.Link;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LinkJpaRepository extends JpaRepository<Link, UUID> {

    List<Link> findByCandidateId(UUID candidateId);
}
