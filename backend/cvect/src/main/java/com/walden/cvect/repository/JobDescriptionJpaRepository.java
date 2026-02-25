package com.walden.cvect.repository;

import com.walden.cvect.model.entity.JobDescription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JobDescriptionJpaRepository extends JpaRepository<JobDescription, UUID> {
}
