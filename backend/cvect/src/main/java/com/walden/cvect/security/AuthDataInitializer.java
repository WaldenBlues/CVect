package com.walden.cvect.security;

import com.walden.cvect.model.TenantConstants;
import com.walden.cvect.model.entity.AuthPermission;
import com.walden.cvect.model.entity.AuthRole;
import com.walden.cvect.model.entity.AuthUser;
import com.walden.cvect.model.entity.Tenant;
import com.walden.cvect.repository.AuthPermissionJpaRepository;
import com.walden.cvect.repository.AuthRoleJpaRepository;
import com.walden.cvect.repository.AuthUserJpaRepository;
import com.walden.cvect.repository.TenantJpaRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class AuthDataInitializer implements ApplicationRunner {

    private static final UUID DEFAULT_TENANT_ID = TenantConstants.DEFAULT_TENANT_ID;

    private final TenantJpaRepository tenantRepository;
    private final AuthPermissionJpaRepository permissionRepository;
    private final AuthRoleJpaRepository roleRepository;
    private final AuthUserJpaRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthDataInitializer(
            TenantJpaRepository tenantRepository,
            AuthPermissionJpaRepository permissionRepository,
            AuthRoleJpaRepository roleRepository,
            AuthUserJpaRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.tenantRepository = tenantRepository;
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensureDefaultTenant();
        Map<String, AuthPermission> permissions = ensurePermissions();
        AuthRole owner = ensureRole("OWNER", "企业管理员", PermissionCodes.ALL, permissions);
        ensureRole("HR_MANAGER", "招聘负责人", PermissionCodes.HR_MANAGER, permissions);
        ensureRole("RECRUITER", "招聘专员", PermissionCodes.RECRUITER, permissions);
        ensureDemoOwner(owner);
    }

    private void ensureDefaultTenant() {
        if (!tenantRepository.existsById(DEFAULT_TENANT_ID)) {
            tenantRepository.save(new Tenant(DEFAULT_TENANT_ID, "Default Tenant", "ACTIVE"));
        }
    }

    private Map<String, AuthPermission> ensurePermissions() {
        Map<String, AuthPermission> permissions = new LinkedHashMap<>();
        for (String code : PermissionCodes.ALL) {
            AuthPermission permission = permissionRepository.findByCode(code)
                    .orElseGet(() -> permissionRepository.save(new AuthPermission(code, code, code)));
            permissions.put(code, permission);
        }
        return permissions;
    }

    private AuthRole ensureRole(
            String code,
            String name,
            List<String> permissionCodes,
            Map<String, AuthPermission> permissions) {
        AuthRole role = roleRepository.findByTenantIdAndCode(DEFAULT_TENANT_ID, code)
                .orElseGet(() -> roleRepository.save(new AuthRole(DEFAULT_TENANT_ID, code, name)));
        for (String permissionCode : permissionCodes) {
            AuthPermission permission = permissions.get(permissionCode);
            if (permission != null) {
                role.getPermissions().add(permission);
            }
        }
        return roleRepository.save(role);
    }

    private void ensureDemoOwner(AuthRole ownerRole) {
        AuthUser user = userRepository.findByTenantIdAndUsername(DEFAULT_TENANT_ID, "demo")
                .orElseGet(() -> userRepository.save(new AuthUser(
                        DEFAULT_TENANT_ID,
                        "demo",
                        passwordEncoder.encode("demo123"),
                        "Demo Owner",
                        true)));
        user.getRoles().add(ownerRole);
        userRepository.save(user);
    }
}
