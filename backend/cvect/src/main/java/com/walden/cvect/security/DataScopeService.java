package com.walden.cvect.security;

import com.walden.cvect.model.entity.JobDescription;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class DataScopeService {

    private static final Set<String> TENANT_WIDE_ROLES = Set.of("OWNER", "HR_MANAGER");

    private final CurrentUserService currentUserService;

    public DataScopeService(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    public boolean hasTenantWideScope() {
        return currentUserService.currentUser()
                .map(this::hasTenantWideScope)
                .orElse(true);
    }

    public boolean restrictsToCurrentUser() {
        return !hasTenantWideScope();
    }

    public UUID currentUserIdOrNull() {
        return currentUserService.currentUserIdOrNull();
    }

    public boolean canAccess(JobDescription jobDescription) {
        if (jobDescription == null) {
            return false;
        }
        if (hasTenantWideScope()) {
            return true;
        }
        UUID userId = currentUserIdOrNull();
        return userId != null && userId.equals(jobDescription.getCreatedByUserId());
    }

    public String cacheScopeKey() {
        UUID tenantId = currentUserService.currentTenantId();
        return currentUserService.currentUser()
                .map(user -> hasTenantWideScope(user)
                        ? "tenant:" + tenantId
                        : "user:" + tenantId + ":" + user.userId())
                .orElse("tenant:" + tenantId);
    }

    private boolean hasTenantWideScope(CurrentUser user) {
        if (user == null) {
            return true;
        }
        boolean tenantWideRole = user.roles() != null && user.roles().stream()
                .filter(role -> role != null && !role.isBlank())
                .map(role -> role.trim().toUpperCase(Locale.ROOT))
                .anyMatch(TENANT_WIDE_ROLES::contains);
        return tenantWideRole || user.hasPermission(PermissionCodes.SYSTEM_ADMIN);
    }
}
