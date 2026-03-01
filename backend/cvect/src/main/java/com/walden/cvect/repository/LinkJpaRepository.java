package com.walden.cvect.repository;

import com.walden.cvect.model.entity.Link;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 链接数据访问
 */
@Repository
public interface LinkJpaRepository extends JpaRepository<Link, UUID> {

    List<Link> findByCandidateId(UUID candidateId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from Link l
            where l.candidateId in (
                select cand.id from Candidate cand where cand.jobDescription.id = :jobDescriptionId
            )
            """)
    int deleteByJobDescriptionId(@Param("jobDescriptionId") UUID jobDescriptionId);
}
