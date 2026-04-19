package com.walden.cvect.model.entity;

import com.walden.cvect.model.TenantConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
        name = "auth_roles",
        uniqueConstraints = @UniqueConstraint(name = "uk_auth_roles_tenant_code", columnNames = {"tenant_id", "code"}))
public class AuthRole {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "code", length = 80, nullable = false)
    private String code;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "auth_role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<AuthPermission> permissions = new LinkedHashSet<>();

    protected AuthRole() {
    }

    public AuthRole(UUID tenantId, String code, String name) {
        this.tenantId = tenantId == null ? TenantConstants.DEFAULT_TENANT_ID : tenantId;
        this.code = code;
        this.name = name;
    }

    @PrePersist
    void onCreate() {
        if (this.tenantId == null) {
            this.tenantId = TenantConstants.DEFAULT_TENANT_ID;
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getCode() {
        return code;
    }

    public Set<AuthPermission> getPermissions() {
        return permissions;
    }
}
