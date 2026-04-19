package com.walden.cvect.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_logs_tenant_created", columnList = "tenant_id,created_at"),
        @Index(name = "idx_audit_logs_user_created", columnList = "user_id,created_at"),
        @Index(name = "idx_audit_logs_action_created", columnList = "action,created_at")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "action", length = 120, nullable = false)
    private String action;

    @Column(name = "target", length = 120)
    private String target;

    @Column(name = "target_id", length = 120)
    private String targetId;

    @Column(name = "status", length = 32, nullable = false)
    private String status;

    @Column(name = "http_method", length = 16)
    private String httpMethod;

    @Column(name = "request_path", columnDefinition = "TEXT")
    private String requestPath;

    @Column(name = "request_id", length = 100)
    private String requestId;

    @Column(name = "client_ip", length = 100)
    private String clientIp;

    @Column(name = "args_summary", columnDefinition = "TEXT")
    private String argsSummary;

    @Column(name = "result_summary", columnDefinition = "TEXT")
    private String resultSummary;

    @Column(name = "error_type", length = 200)
    private String errorType;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public AuditLog() {
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public void setRequestPath(String requestPath) {
        this.requestPath = requestPath;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public void setArgsSummary(String argsSummary) {
        this.argsSummary = argsSummary;
    }

    public void setResultSummary(String resultSummary) {
        this.resultSummary = resultSummary;
    }

    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getAction() {
        return action;
    }

    public String getTarget() {
        return target;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getStatus() {
        return status;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getRequestPath() {
        return requestPath;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getClientIp() {
        return clientIp;
    }

    public String getArgsSummary() {
        return argsSummary;
    }

    public String getResultSummary() {
        return resultSummary;
    }

    public String getErrorType() {
        return errorType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
