package com.walden.cvect.logging.aop;

import com.walden.cvect.logging.config.LogProperties;
import com.walden.cvect.logging.support.LogValueSanitizer;
import com.walden.cvect.logging.support.WebLogFormatter;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;

@Aspect
@Component
public class AuditActionAspect {

    private final LogProperties properties;
    private final LogValueSanitizer sanitizer;
    private final WebLogFormatter formatter;

    public AuditActionAspect(LogProperties properties, LogValueSanitizer sanitizer, WebLogFormatter formatter) {
        this.properties = properties;
        this.sanitizer = sanitizer;
        this.formatter = formatter;
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
}
