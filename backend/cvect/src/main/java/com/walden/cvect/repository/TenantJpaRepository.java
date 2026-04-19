package com.walden.cvect.repository;

import com.walden.cvect.model.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TenantJpaRepository extends JpaRepository<Tenant, UUID> {
}
