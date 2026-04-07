package com.walden.cvect.logging.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.logging.web")
public class LogProperties {

    private boolean enabled = true;
    private String requestIdHeader = "X-Request-Id";
    private long slowThresholdMs = 500L;
    private int maxStringLength = 120;
    private boolean logRequestStart = true;
    private boolean logRequestEnd = true;
    private final ControllerConfig controller = new ControllerConfig();
    private final ServiceConfig service = new ServiceConfig();
    private final AuditConfig audit = new AuditConfig();
    private final TimingConfig timing = new TimingConfig();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRequestIdHeader() {
        return requestIdHeader;
    }

    public void setRequestIdHeader(String requestIdHeader) {
        this.requestIdHeader = requestIdHeader;
    }

    public long getSlowThresholdMs() {
        return slowThresholdMs;
    }

    public void setSlowThresholdMs(long slowThresholdMs) {
        this.slowThresholdMs = slowThresholdMs;
    }

    public int getMaxStringLength() {
        return maxStringLength;
    }

    public void setMaxStringLength(int maxStringLength) {
        this.maxStringLength = maxStringLength;
    }

    public boolean isLogRequestStart() {
        return logRequestStart;
    }

    public void setLogRequestStart(boolean logRequestStart) {
        this.logRequestStart = logRequestStart;
    }

    public boolean isLogRequestEnd() {
        return logRequestEnd;
    }

    public void setLogRequestEnd(boolean logRequestEnd) {
        this.logRequestEnd = logRequestEnd;
    }

    public ControllerConfig getController() {
        return controller;
    }

    public ServiceConfig getService() {
        return service;
    }

    public AuditConfig getAudit() {
        return audit;
    }

    public TimingConfig getTiming() {
        return timing;
    }

    public static class ControllerConfig {
        private boolean enabled = true;
        private boolean logArgs = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isLogArgs() {
            return logArgs;
        }

        public void setLogArgs(boolean logArgs) {
            this.logArgs = logArgs;
        }
    }

    public static class ServiceConfig {
        private boolean enabled = true;
        private boolean logArgs = true;
        private boolean logResult = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isLogArgs() {
            return logArgs;
        }

        public void setLogArgs(boolean logArgs) {
            this.logArgs = logArgs;
        }

        public boolean isLogResult() {
            return logResult;
        }

        public void setLogResult(boolean logResult) {
            this.logResult = logResult;
        }
    }

    public static class AuditConfig {
        private boolean enabled = true;
        private boolean logArgs = true;
        private boolean logResult = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isLogArgs() {
            return logArgs;
        }

        public void setLogArgs(boolean logArgs) {
            this.logArgs = logArgs;
        }

        public boolean isLogResult() {
            return logResult;
        }

        public void setLogResult(boolean logResult) {
            this.logResult = logResult;
        }
    }

    public static class TimingConfig {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
