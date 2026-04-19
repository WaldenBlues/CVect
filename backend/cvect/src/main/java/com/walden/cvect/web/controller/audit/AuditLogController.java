package com.walden.cvect.web.controller.audit;

import com.walden.cvect.model.entity.AuditLog;
import com.walden.cvect.repository.AuditLogJpaRepository;
import com.walden.cvect.security.CurrentUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private final AuditLogJpaRepository auditLogRepository;
    private final CurrentUserService currentUserService;

    public AuditLogController(AuditLogJpaRepository auditLogRepository, CurrentUserService currentUserService) {
        this.auditLogRepository = auditLogRepository;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    @PreAuthorize("@permissionGuard.has(T(com.walden.cvect.security.PermissionCodes).AUDIT_READ)")
    public ResponseEntity<AuditLogPageResponse> list(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(200, Math.max(1, size));
        Page<AuditLog> logs = auditLogRepository.findByTenantIdOrderByCreatedAtDesc(
                currentUserService.currentTenantId(),
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(AuditLogPageResponse.fromPage(logs));
    }

    public record AuditLogPageResponse(
            List<AuditLogItem> content,
            int number,
            int size,
            long totalElements,
            int totalPages) {

        static AuditLogPageResponse fromPage(Page<AuditLog> page) {
            return new AuditLogPageResponse(
                    page.getContent().stream().map(AuditLogItem::fromEntity).toList(),
                    page.getNumber(),
                    page.getSize(),
                    page.getTotalElements(),
                    page.getTotalPages());
        }
    }

    public record AuditLogItem(
            UUID id,
            UUID tenantId,
            UUID userId,
            String username,
            String action,
            String target,
            String targetId,
            String status,
            String httpMethod,
            String requestPath,
            String requestId,
            String clientIp,
            String argsSummary,
            String resultSummary,
            String errorType,
            String errorMessage,
            LocalDateTime createdAt) {

        static AuditLogItem fromEntity(AuditLog log) {
            return new AuditLogItem(
                    log.getId(),
                    log.getTenantId(),
                    log.getUserId(),
                    log.getUsername(),
                    log.getAction(),
                    log.getTarget(),
                    log.getTargetId(),
                    log.getStatus(),
                    log.getHttpMethod(),
                    log.getRequestPath(),
                    log.getRequestId(),
                    log.getClientIp(),
                    log.getArgsSummary(),
                    log.getResultSummary(),
                    log.getErrorType(),
                    log.getErrorMessage(),
                    log.getCreatedAt());
        }
    }
}
