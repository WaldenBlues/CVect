package com.walden.cvect.model.entity;

import com.walden.cvect.model.TenantConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tenants")
public class Tenant {

    @Id
    private UUID id;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Column(name = "status", length = 32, nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Tenant() {
    }

    public Tenant(UUID id, String name, String status) {
        this.id = id == null ? TenantConstants.DEFAULT_TENANT_ID : id;
        this.name = name;
        this.status = status == null ? "ACTIVE" : status;
    }

    @PrePersist
    void onCreate() {
        if (this.id == null) {
            this.id = TenantConstants.DEFAULT_TENANT_ID;
        }
        if (this.status == null) {
            this.status = "ACTIVE";
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
