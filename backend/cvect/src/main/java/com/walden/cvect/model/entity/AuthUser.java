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
        name = "auth_users",
        uniqueConstraints = @UniqueConstraint(name = "uk_auth_users_tenant_username", columnNames = {"tenant_id", "username"}))
public class AuthUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "username", length = 100, nullable = false)
    private String username;

    @Column(name = "password_hash", length = 255, nullable = false)
    private String passwordHash;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "auth_user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<AuthRole> roles = new LinkedHashSet<>();

    protected AuthUser() {
    }

    public AuthUser(UUID tenantId, String username, String passwordHash, String displayName, boolean enabled) {
        this.tenantId = tenantId == null ? TenantConstants.DEFAULT_TENANT_ID : tenantId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.enabled = enabled;
    }

    @PrePersist
    void onCreate() {
        if (this.tenantId == null) {
            this.tenantId = TenantConstants.DEFAULT_TENANT_ID;
        }
        if (this.enabled == null) {
            this.enabled = true;
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

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    public Set<AuthRole> getRoles() {
        return roles;
    }
}
