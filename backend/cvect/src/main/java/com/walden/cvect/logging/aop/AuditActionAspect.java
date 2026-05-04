package com.walden.cvect.logging.aop;

import com.walden.cvect.logging.config.LogProperties;
import com.walden.cvect.logging.support.LogValueSanitizer;
import com.walden.cvect.logging.support.WebLogFormatter;
import com.walden.cvect.model.entity.AuditLog;
import com.walden.cvect.repository.AuditLogJpaRepository;
import com.walden.cvect.security.CurrentUser;
import com.walden.cvect.security.CurrentUserService;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Aspect
@Component
public class AuditActionAspect {

    private final LogProperties properties;
    private final LogValueSanitizer sanitizer;
    private final WebLogFormatter formatter;
    private final AuditLogJpaRepository auditLogRepository;
    private final CurrentUserService currentUserService;
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public AuditActionAspect(
            LogProperties properties,
            LogValueSanitizer sanitizer,
            WebLogFormatter formatter,
            AuditLogJpaRepository auditLogRepository,
            CurrentUserService currentUserService) {
        this.properties = properties;
        this.sanitizer = sanitizer;
        this.formatter = formatter;
        this.auditLogRepository = auditLogRepository;
        this.currentUserService = currentUserService;
    }

    @Around("@annotation(auditAction)")
    public Object around(ProceedingJoinPoint joinPoint, AuditAction auditAction) throws Throwable {
        if (!properties.isEnabled() || !properties.getAudit().isEnabled()) {
            return joinPoint.proceed();
        }

        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Class<?> targetClass = resolveTargetClass(joinPoint, method);
        Logger targetLogger = LoggerFactory.getLogger(targetClass);
        String argsSummary = properties.getAudit().isLogArgs() && auditAction.logArgs()
                ? sanitizer.summarizeMethodArguments(method, joinPoint.getArgs())
                : "";

        try {
            Object result = joinPoint.proceed();
            LinkedHashMap<String, Object> fields = baseFields(auditAction, targetClass, method, "success");
            if (StringUtils.hasText(argsSummary)) {
                fields.put("args", argsSummary);
            }
            if (properties.getAudit().isLogResult() && auditAction.logResult()) {
                fields.put("result", sanitizer.summarizeReturnValue(result));
            }
            targetLogger.info(formatter.format("audit_action", fields));
            persistAuditLog(auditAction, joinPoint, method, "success", argsSummary, result, null);
            return result;
        } catch (Throwable ex) {
            LinkedHashMap<String, Object> fields = baseFields(auditAction, targetClass, method, "error");
            if (StringUtils.hasText(argsSummary)) {
                fields.put("args", argsSummary);
            }
            fields.put("exception", ex.getClass().getSimpleName());
            if (StringUtils.hasText(ex.getMessage())) {
                fields.put("message", sanitizer.summarizeExceptionMessage(ex));
            }
            targetLogger.warn(formatter.format("audit_action", fields));
            persistAuditLog(auditAction, joinPoint, method, "error", argsSummary, null, ex);
            throw ex;
        }
    }

    private LinkedHashMap<String, Object> baseFields(
            AuditAction auditAction,
            Class<?> targetClass,
            Method method,
            String status) {
        LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
        fields.put("action", auditAction.action());
        if (StringUtils.hasText(auditAction.target())) {
            fields.put("target", auditAction.target());
        }
        fields.put("class", targetClass.getSimpleName());
        fields.put("method", method.getName());
        fields.put("status", status);
        return fields;
    }

    private Class<?> resolveTargetClass(ProceedingJoinPoint joinPoint, Method method) {
        Object target = joinPoint.getTarget();
        return target != null ? target.getClass() : method.getDeclaringClass();
    }

    private void persistAuditLog(
            AuditAction auditAction,
            ProceedingJoinPoint joinPoint,
            Method method,
            String status,
            String argsSummary,
            Object result,
            Throwable error) {
        try {
            AuditLog auditLog = new AuditLog();
            Optional<CurrentUser> currentUser = currentUserService.currentUser();
            populateActor(auditLog, currentUser, method, joinPoint.getArgs(), result);
            auditLog.setAction(auditAction.action());
            auditLog.setTarget(StringUtils.hasText(auditAction.target()) ? auditAction.target() : method.getName());
            auditLog.setTargetId(extractTargetId(joinPoint.getArgs()));
            auditLog.setStatus(status);
            auditLog.setHttpMethod(MDC.get("httpMethod"));
            auditLog.setRequestPath(MDC.get("requestPath"));
            auditLog.setRequestId(MDC.get("requestId"));
            auditLog.setClientIp(MDC.get("clientIp"));
            if (StringUtils.hasText(argsSummary)) {
                auditLog.setArgsSummary(argsSummary);
            }
            if (result != null && properties.getAudit().isLogResult() && auditAction.logResult()) {
                auditLog.setResultSummary(sanitizer.summarizeReturnValue(result));
            }
            if (error != null) {
                auditLog.setErrorType(error.getClass().getSimpleName());
                if (StringUtils.hasText(error.getMessage())) {
                    auditLog.setErrorMessage(sanitizer.summarizeExceptionMessage(error));
                }
            }
            auditLogRepository.save(auditLog);
        } catch (RuntimeException auditFailure) {
            LoggerFactory.getLogger(AuditActionAspect.class)
                    .warn("Failed to persist audit log for action={}", auditAction.action(), auditFailure);
        }
    }

    private void populateActor(
            AuditLog auditLog,
            Optional<CurrentUser> currentUser,
            Method method,
            Object[] args,
            Object result) {
        if (currentUser.isPresent()) {
            CurrentUser user = currentUser.get();
            auditLog.setTenantId(user.tenantId());
            auditLog.setUserId(user.userId());
            auditLog.setUsername(user.username());
            return;
        }
        ExtractedActor actor = extractActor(method, args).merge(extractActorFromNamedValue("result", result));
        if (actor.tenantId() != null) {
            auditLog.setTenantId(actor.tenantId());
        }
        if (actor.userId() != null) {
            auditLog.setUserId(actor.userId());
        }
        if (StringUtils.hasText(actor.username())) {
            auditLog.setUsername(actor.username());
        }
    }

    private ExtractedActor extractActor(Method method, Object[] args) {
        if (args == null || args.length == 0) {
            return ExtractedActor.empty();
        }
        String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
        ExtractedActor extracted = ExtractedActor.empty();
        for (int i = 0; i < args.length; i++) {
            String parameterName = parameterNames != null && i < parameterNames.length && StringUtils.hasText(parameterNames[i])
                    ? parameterNames[i]
                    : "arg" + i;
            extracted = extracted.merge(extractActorFromNamedValue(parameterName, args[i]));
            if (extracted.isComplete()) {
                return extracted;
            }
        }
        return extracted;
    }

    private ExtractedActor extractActorFromNamedValue(String name, Object value) {
        if (value == null) {
            return ExtractedActor.empty();
        }
        if (value instanceof CurrentUser user) {
            return new ExtractedActor(user.tenantId(), user.userId(), user.username());
        }
        if (value instanceof ResponseEntity<?> responseEntity) {
            return extractActorFromNamedValue(name, responseEntity.getBody());
        }
        if (value instanceof Map<?, ?> map) {
            return extractActorFromMap(map);
        }
        if (value.getClass().isRecord()) {
            return extractActorFromRecord(value);
        }
        return switch (normalizeName(name)) {
            case "tenantid" -> new ExtractedActor(parseUuid(value), null, null);
            case "userid" -> new ExtractedActor(null, parseUuid(value), null);
            case "username" -> new ExtractedActor(null, null, stringify(value));
            default -> ExtractedActor.empty();
        };
    }

    private ExtractedActor extractActorFromMap(Map<?, ?> map) {
        ExtractedActor extracted = ExtractedActor.empty();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            extracted = extracted.merge(extractActorFromNamedValue(String.valueOf(entry.getKey()), entry.getValue()));
            if (extracted.isComplete()) {
                return extracted;
            }
        }
        return extracted;
    }

    private ExtractedActor extractActorFromRecord(Object value) {
        RecordComponent[] components = value.getClass().getRecordComponents();
        if (components == null || components.length == 0) {
            return ExtractedActor.empty();
        }
        boolean userLikeRecord = normalizeName(value.getClass().getSimpleName()).contains("user");
        ExtractedActor extracted = ExtractedActor.empty();
        for (RecordComponent component : components) {
            try {
                Object nestedValue = component.getAccessor().invoke(value);
                if (userLikeRecord && "id".equals(normalizeName(component.getName()))) {
                    extracted = extracted.merge(new ExtractedActor(null, parseUuid(nestedValue), null));
                } else {
                    extracted = extracted.merge(extractActorFromNamedValue(component.getName(), nestedValue));
                }
                if (extracted.isComplete()) {
                    return extracted;
                }
            } catch (Exception ignored) {
                // Best-effort fallback only for audit actor enrichment.
            }
        }
        return extracted;
    }

    private UUID parseUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        String text = stringify(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return UUID.fromString(text.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String stringify(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
    }

    private record ExtractedActor(UUID tenantId, UUID userId, String username) {

        private static ExtractedActor empty() {
            return new ExtractedActor(null, null, null);
        }

        private ExtractedActor merge(ExtractedActor other) {
            if (other == null) {
                return this;
            }
            return new ExtractedActor(
                    tenantId != null ? tenantId : other.tenantId,
                    userId != null ? userId : other.userId,
                    StringUtils.hasText(username) ? username : other.username);
        }

        private boolean isComplete() {
            return tenantId != null && userId != null && StringUtils.hasText(username);
        }
    }

    private String extractTargetId(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof UUID uuid) {
                return uuid.toString();
            }
            if (arg instanceof CharSequence text && looksLikeUuid(text.toString())) {
                return text.toString();
            }
        }
        return null;
    }

    private boolean looksLikeUuid(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        try {
            UUID.fromString(value.trim());
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
