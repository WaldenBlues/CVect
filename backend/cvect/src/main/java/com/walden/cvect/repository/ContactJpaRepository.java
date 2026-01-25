package com.walden.cvect.repository;

import com.walden.cvect.model.entity.Contact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 联系方式数据访问
 */
@Repository
public interface ContactJpaRepository extends JpaRepository<Contact, UUID> {

    List<Contact> findByCandidateId(UUID candidateId);
}
