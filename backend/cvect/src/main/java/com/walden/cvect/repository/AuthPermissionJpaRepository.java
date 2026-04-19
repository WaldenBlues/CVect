package com.walden.cvect.repository;

import com.walden.cvect.model.entity.AuthPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AuthPermissionJpaRepository extends JpaRepository<AuthPermission, UUID> {

    Optional<AuthPermission> findByCode(String code);
}
