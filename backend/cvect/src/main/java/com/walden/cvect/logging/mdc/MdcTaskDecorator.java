package com.walden.cvect.logging.mdc;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> capturedContext = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previousContext = MDC.getCopyOfContextMap();
            try {
                if (capturedContext == null || capturedContext.isEmpty()) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(capturedContext);
                }
                runnable.run();
            } finally {
                if (previousContext == null || previousContext.isEmpty()) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(previousContext);
                }
            }
        };
    }
}
