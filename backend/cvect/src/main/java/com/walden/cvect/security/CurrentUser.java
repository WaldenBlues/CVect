package com.walden.cvect.security;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record CurrentUser(
        UUID userId,
        UUID tenantId,
        String username,
        String displayName,
        List<String> roles,
        Set<String> permissions) {

    public boolean hasPermission(String permission) {
        return permissions != null && permissions.contains(permission);
    }
}
