package com.walden.cvect.repository;

import com.walden.cvect.model.entity.AuthRole;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AuthRoleJpaRepository extends JpaRepository<AuthRole, UUID> {

    @EntityGraph(attributePaths = "permissions")
    Optional<AuthRole> findByTenantIdAndCode(UUID tenantId, String code);
}
