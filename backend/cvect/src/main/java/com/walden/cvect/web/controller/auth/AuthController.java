package com.walden.cvect.web.controller.auth;

import com.walden.cvect.logging.aop.AuditAction;
import com.walden.cvect.model.TenantConstants;
import com.walden.cvect.security.AuthService;
import com.walden.cvect.security.CurrentUser;
import com.walden.cvect.security.CurrentUserService;
import com.walden.cvect.security.JwtAuthenticationFilter;
import com.walden.cvect.security.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final CurrentUserService currentUserService;

    public AuthController(AuthService authService, JwtService jwtService, CurrentUserService currentUserService) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/login")
    @AuditAction(action = "login", target = "auth", logResult = true)
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse servletResponse) {
        try {
            UUID tenantId = parseTenantId(request.tenantId());
            CurrentUser user = authService.login(tenantId, request.username(), request.password());
            String token = jwtService.createToken(user);
            servletResponse.addHeader(HttpHeaders.SET_COOKIE, accessTokenCookie(token, jwtService.ttlSeconds()).toString());
            return ResponseEntity.ok(new LoginResponse(token, toUserResponse(user)));
        } catch (AuthenticationException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password", ex);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        JwtAuthenticationFilter.clearAccessCookie(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me() {
        return currentUserService.currentUser()
                .map(user -> ResponseEntity.ok(toUserResponse(user)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null));
    }

    private static UUID parseTenantId(String rawTenantId) {
        if (!StringUtils.hasText(rawTenantId)) {
            return TenantConstants.DEFAULT_TENANT_ID;
        }
        try {
            return UUID.fromString(rawTenantId.trim());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid tenant", ex);
        }
    }

    private static ResponseCookie accessTokenCookie(String token, long ttlSeconds) {
        return ResponseCookie.from(JwtAuthenticationFilter.ACCESS_TOKEN_COOKIE, token)
                .httpOnly(true)
                .path("/")
                .sameSite("Lax")
                .maxAge(Duration.ofSeconds(ttlSeconds))
                .build();
    }

    private static UserResponse toUserResponse(CurrentUser user) {
        return new UserResponse(
                user.userId(),
                user.tenantId(),
                user.username(),
                user.displayName(),
                user.roles(),
                user.permissions());
    }

    public record LoginRequest(
            String tenantId,
            @NotBlank String username,
            @NotBlank String password) {
    }

    public record LoginResponse(String accessToken, UserResponse user) {
    }

    public record UserResponse(
            UUID id,
            UUID tenantId,
            String username,
            String displayName,
            List<String> roles,
            Set<String> permissions) {
    }
}
