package com.walden.cvect.security;

import com.walden.cvect.model.TenantConstants;
import com.walden.cvect.model.entity.AuthPermission;
import com.walden.cvect.model.entity.AuthRole;
import com.walden.cvect.model.entity.AuthUser;
import com.walden.cvect.repository.AuthUserJpaRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthService {

    private final AuthUserJpaRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AuthUserJpaRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public CurrentUser login(UUID tenantId, String username, String password) {
        UUID resolvedTenantId = tenantId == null ? TenantConstants.DEFAULT_TENANT_ID : tenantId;
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new BadCredentialsException("Invalid username or password");
        }
        AuthUser user = userRepository.findByTenantIdAndUsername(resolvedTenantId, username.trim())
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));
        if (!user.isEnabled()) {
            throw new DisabledException("User is disabled");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }
        return toCurrentUser(user);
    }

    @Transactional(readOnly = true)
    public CurrentUser loadCurrentUser(UUID userId) {
        AuthUser user = userRepository.findWithRolesById(userId)
                .orElseThrow(() -> new BadCredentialsException("Invalid token"));
        if (!user.isEnabled()) {
            throw new DisabledException("User is disabled");
        }
        return toCurrentUser(user);
    }

    public CurrentUser toCurrentUser(AuthUser user) {
        List<String> roles = user.getRoles().stream()
                .map(AuthRole::getCode)
                .sorted()
                .toList();
        Set<String> permissions = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(AuthPermission::getCode)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return new CurrentUser(
                user.getId(),
                user.getTenantId(),
                user.getUsername(),
                user.getDisplayName(),
                roles.stream().sorted(Comparator.naturalOrder()).toList(),
                permissions);
    }
}
