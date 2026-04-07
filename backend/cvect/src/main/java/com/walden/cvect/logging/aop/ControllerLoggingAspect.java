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
public class ControllerLoggingAspect {

    private final LogProperties properties;
    private final LogValueSanitizer sanitizer;
    private final WebLogFormatter formatter;

    public ControllerLoggingAspect(LogProperties properties, LogValueSanitizer sanitizer, WebLogFormatter formatter) {
        this.properties = properties;
        this.sanitizer = sanitizer;
        this.formatter = formatter;
    }

    @Around("execution(public * com.walden.cvect.web.controller..*.*(..))"
            + " && !within(com.walden.cvect.web.controller.upload.BatchSseController)"
            + " && !within(com.walden.cvect.web.controller.candidate.CandidateStreamController)"
            + " && !within(com.walden.cvect.web.controller.search.SearchController)"
            + " && !within(com.walden.cvect.web.controller.system.VectorHealthController)"
            + " && !execution(* com.walden.cvect.web.controller.resume.ResumeController.health(..))")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!properties.isEnabled() || !properties.getController().isEnabled()) {
            return joinPoint.proceed();
        }

        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Class<?> targetClass = resolveTargetClass(joinPoint, method);
        Logger targetLogger = LoggerFactory.getLogger(targetClass);
        String argsSummary = properties.getController().isLogArgs()
                ? sanitizer.summarizeMethodArguments(method, joinPoint.getArgs())
                : "";
        long startNanos = System.nanoTime();

        try {
            Object result = joinPoint.proceed();
            long costMs = elapsedMillis(startNanos);
            LinkedHashMap<String, Object> fields = baseFields(targetClass, method, "success", costMs);
            if (StringUtils.hasText(argsSummary)) {
                fields.put("args", argsSummary);
            }
            logSuccess(targetLogger, formatter.format("controller_invoke", fields), costMs);
            return result;
        } catch (Throwable ex) {
            long costMs = elapsedMillis(startNanos);
            LinkedHashMap<String, Object> fields = baseFields(targetClass, method, "error", costMs);
            if (StringUtils.hasText(argsSummary)) {
                fields.put("args", argsSummary);
            }
            fields.put("exception", ex.getClass().getSimpleName());
            if (StringUtils.hasText(ex.getMessage())) {
                fields.put("message", sanitizer.summarizeExceptionMessage(ex));
            }
            targetLogger.warn(formatter.format("controller_invoke", fields));
            throw ex;
        }
    }

    private LinkedHashMap<String, Object> baseFields(Class<?> targetClass, Method method, String status, long costMs) {
        LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
        fields.put("class", targetClass.getSimpleName());
        fields.put("method", method.getName());
        fields.put("status", status);
        fields.put("costMs", costMs);
        return fields;
    }

    private void logSuccess(Logger logger, String message, long costMs) {
        if (properties.getSlowThresholdMs() > 0 && costMs >= properties.getSlowThresholdMs()) {
            logger.warn(message);
            return;
        }
        logger.info(message);
    }

    private long elapsedMillis(long startNanos) {
        return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
    }

    private Class<?> resolveTargetClass(ProceedingJoinPoint joinPoint, Method method) {
        Object target = joinPoint.getTarget();
        return target != null ? target.getClass() : method.getDeclaringClass();
    }
}
