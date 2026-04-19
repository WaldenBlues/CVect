package com.walden.cvect.security;

import com.walden.cvect.model.TenantConstants;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class CurrentUserService {

    public Optional<CurrentUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof CurrentUser currentUser) {
            return Optional.of(currentUser);
        }
        return Optional.empty();
    }

    public UUID currentTenantId() {
        return currentUser()
                .map(CurrentUser::tenantId)
                .orElse(TenantConstants.DEFAULT_TENANT_ID);
    }

    public UUID currentUserIdOrNull() {
        return currentUser()
                .map(CurrentUser::userId)
                .orElse(null);
    }

    public String currentUsernameOrNull() {
        return currentUser()
                .map(CurrentUser::username)
                .orElse(null);
    }
}
