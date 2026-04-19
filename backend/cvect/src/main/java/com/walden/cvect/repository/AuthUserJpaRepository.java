package com.walden.cvect.repository;

import com.walden.cvect.model.entity.AuthUser;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AuthUserJpaRepository extends JpaRepository<AuthUser, UUID> {

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<AuthUser> findByTenantIdAndUsername(UUID tenantId, String username);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    @Query("select u from AuthUser u where u.id = :id")
    Optional<AuthUser> findWithRolesById(@Param("id") UUID id);
}
