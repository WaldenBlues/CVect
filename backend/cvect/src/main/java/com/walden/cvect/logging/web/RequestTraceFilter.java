package com.walden.cvect.logging.web;

import com.walden.cvect.logging.config.LogProperties;
import com.walden.cvect.logging.support.WebLogConstants;
import com.walden.cvect.logging.support.WebLogFormatter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RequestTraceFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestTraceFilter.class);

    private final LogProperties properties;
    private final WebLogFormatter formatter;

    public RequestTraceFilter(LogProperties properties, WebLogFormatter formatter) {
        this.properties = properties;
        this.formatter = formatter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        String path = request.getRequestURI();
        return "/api/resumes/health".equals(path)
                || "/api/candidates/stream".equals(path)
                || path.startsWith("/api/sse/batches/")
                || path.startsWith("/actuator")
                || (path.startsWith("/api/uploads/batches/") && path.endsWith("/stream"));
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return true;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        String requestPath = request.getRequestURI();
        String requestMethod = request.getMethod();
        long startNanos = System.nanoTime();

        request.setAttribute(WebLogConstants.REQUEST_ID_ATTRIBUTE, requestId);
        response.setHeader(properties.getRequestIdHeader(), requestId);
        putRequestContext(requestId, requestMethod, requestPath, resolveClientIp(request));

        if (properties.isLogRequestStart()) {
            log.info(formatter.format("http_request_start", requestStartFields(request)));
        }

        Throwable failure = null;
        try {
            filterChain.doFilter(request, response);
        } catch (Throwable ex) {
            failure = ex;
            logRequestError(request, response, ex);
            rethrow(ex);
        } finally {
            if (properties.isLogRequestEnd()) {
                log.info(formatter.format("http_request_end",
                        requestEndFields(request, response, failure, elapsedMillis(startNanos))));
            }
            clearRequestContext();
        }
    }

    private Map<String, Object> requestStartFields(HttpServletRequest request) {
        LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
        fields.put("method", request.getMethod());
        fields.put("path", request.getRequestURI());
        String queryKeys = summarizeQueryKeys(request);
        if (StringUtils.hasText(queryKeys)) {
            fields.put("queryKeys", queryKeys);
        }
        fields.put("clientIp", resolveClientIp(request));
        return fields;
    }

    private Map<String, Object> requestEndFields(
            HttpServletRequest request,
            HttpServletResponse response,
            Throwable failure,
            long costMs) {
        LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
        fields.put("method", request.getMethod());
        fields.put("path", request.getRequestURI());
        fields.put("status", resolveStatus(response, failure));
        fields.put("costMs", costMs);
        return fields;
    }

    private void logRequestError(HttpServletRequest request, HttpServletResponse response, Throwable throwable) {
        LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
        fields.put("handler", "filter");
        fields.put("method", request.getMethod());
        fields.put("path", request.getRequestURI());
        fields.put("status", resolveStatus(response, throwable));
        fields.put("exception", throwable.getClass().getSimpleName());
        if (StringUtils.hasText(throwable.getMessage())) {
            fields.put("message", throwable.getMessage());
        }
        log.error(formatter.format("http_request_error", fields), throwable);
    }

    private String resolveRequestId(HttpServletRequest request) {
        String headerName = properties.getRequestIdHeader();
        String existing = StringUtils.hasText(headerName) ? request.getHeader(headerName) : null;
        return StringUtils.hasText(existing) ? existing.trim() : UUID.randomUUID().toString();
    }

    private String summarizeQueryKeys(HttpServletRequest request) {
        if (request.getParameterMap().isEmpty()) {
            return "";
        }
        return String.join(",", request.getParameterMap().keySet());
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            int commaIndex = forwardedFor.indexOf(',');
            return commaIndex >= 0 ? forwardedFor.substring(0, commaIndex).trim() : forwardedFor.trim();
        }
        return request.getRemoteAddr();
    }

    private int resolveStatus(HttpServletResponse response, Throwable failure) {
        int currentStatus = response.getStatus();
        if (currentStatus >= 400) {
            return currentStatus;
        }
        return failure == null ? currentStatus : 500;
    }

    private long elapsedMillis(long startNanos) {
        return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
    }

    private void putRequestContext(String requestId, String method, String path, String clientIp) {
        MDC.put("requestId", requestId);
        MDC.put("httpMethod", method);
        MDC.put("requestPath", path);
        MDC.put("clientIp", clientIp);
    }

    private void clearRequestContext() {
        MDC.remove("requestId");
        MDC.remove("httpMethod");
        MDC.remove("requestPath");
        MDC.remove("clientIp");
    }

    private void rethrow(Throwable throwable) throws ServletException, IOException {
        if (throwable instanceof ServletException servletException) {
            throw servletException;
        }
        if (throwable instanceof IOException ioException) {
            throw ioException;
        }
        if (throwable instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (throwable instanceof Error error) {
            throw error;
        }
        throw new ServletException(throwable);
    }
}
