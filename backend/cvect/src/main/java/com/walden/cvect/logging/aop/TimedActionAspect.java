package com.walden.cvect.logging.aop;

import com.walden.cvect.logging.config.LogProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@Aspect
@Component
public class TimedActionAspect {

    private final LogProperties properties;
    private final MeterRegistry meterRegistry;

    public TimedActionAspect(LogProperties properties, ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.properties = properties;
        this.meterRegistry = meterRegistryProvider.getIfAvailable();
    }

    @Around("@annotation(timedAction)")
    public Object around(ProceedingJoinPoint joinPoint, TimedAction timedAction) throws Throwable {
        if (!properties.isEnabled() || !properties.getTiming().isEnabled() || meterRegistry == null) {
            return joinPoint.proceed();
        }

        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Class<?> targetClass = resolveTargetClass(joinPoint, method);
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "success";
        try {
            return joinPoint.proceed();
        } catch (Throwable ex) {
            outcome = "error";
            throw ex;
        } finally {
            sample.stop(Timer.builder(timedAction.metric())
                    .description(timedAction.description())
                    .tags(resolveTags(timedAction, targetClass, method, outcome))
                    .register(meterRegistry));
        }
    }

    private String[] resolveTags(TimedAction timedAction, Class<?> targetClass, Method method, String outcome) {
        List<String> tags = new ArrayList<>();
        tags.add("class");
        tags.add(targetClass.getSimpleName());
        tags.add("method");
        tags.add(method.getName());
        tags.add("outcome");
        tags.add(outcome);

        String[] configuredTags = timedAction.tags();
        if (configuredTags.length % 2 != 0) {
            throw new IllegalStateException("@TimedAction tags must be key/value pairs");
        }
        for (int i = 0; i < configuredTags.length; i += 2) {
            tags.add(configuredTags[i]);
            tags.add(configuredTags[i + 1]);
        }
        return tags.toArray(String[]::new);
    }

    private Class<?> resolveTargetClass(ProceedingJoinPoint joinPoint, Method method) {
        Object target = joinPoint.getTarget();
        return target != null ? target.getClass() : method.getDeclaringClass();
    }
}
