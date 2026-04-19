package com.walden.cvect.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component("permissionGuard")
public class PermissionGuard {

    private final CurrentUserService currentUserService;
    private final boolean securityEnabled;

    public PermissionGuard(
            CurrentUserService currentUserService,
            @Value("${app.security.enabled:true}") boolean securityEnabled) {
        this.currentUserService = currentUserService;
        this.securityEnabled = securityEnabled;
    }

    public boolean has(String permission) {
        if (!securityEnabled) {
            return true;
        }
        return currentUserService.currentUser()
                .map(user -> user.hasPermission(permission))
                .orElse(false);
    }
}
