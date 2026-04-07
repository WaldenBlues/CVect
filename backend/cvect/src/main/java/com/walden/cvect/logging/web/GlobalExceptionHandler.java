package com.walden.cvect.logging.web;

import com.walden.cvect.logging.config.LogProperties;
import com.walden.cvect.logging.support.WebLogConstants;
import com.walden.cvect.logging.support.WebLogFormatter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.validation.BindException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final LogProperties properties;
    private final WebLogFormatter formatter;

    public GlobalExceptionHandler(LogProperties properties, WebLogFormatter formatter) {
        this.properties = properties;
        this.formatter = formatter;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnhandled(Exception ex, HttpServletRequest request) {
        return buildResponse(ex, resolveStatus(ex), new HttpHeaders(), request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            @Nullable Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        HttpServletRequest servletRequest = request instanceof ServletWebRequest servletWebRequest
                ? servletWebRequest.getRequest()
                : null;
        return buildResponse(ex, statusCode, headers, servletRequest);
    }

    private ResponseEntity<Object> buildResponse(
            Exception ex,
            HttpStatusCode statusCode,
            HttpHeaders headers,
            @Nullable HttpServletRequest request) {
        HttpStatusCode effectiveStatus = statusCode == null ? HttpStatus.INTERNAL_SERVER_ERROR : statusCode;
        HttpHeaders responseHeaders = HttpHeaders.writableHttpHeaders(headers == null ? new HttpHeaders() : headers);
        String requestId = resolveRequestId(request);
        if (StringUtils.hasText(properties.getRequestIdHeader()) && StringUtils.hasText(requestId)) {
            responseHeaders.set(properties.getRequestIdHeader(), requestId);
        }
        String path = request == null ? "" : request.getRequestURI();
        int status = effectiveStatus.value();
        ApiErrorResponse payload = new ApiErrorResponse(
                status,
                resolveClientMessage(ex, status),
                requestId,
                path,
                OffsetDateTime.now());
        logHttpError(ex, request, status);
        return new ResponseEntity<>(payload, responseHeaders, effectiveStatus);
    }

    private void logHttpError(Exception ex, @Nullable HttpServletRequest request, int status) {
        LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
        fields.put("handler", "advice");
        fields.put("method", request == null ? "" : request.getMethod());
        fields.put("path", request == null ? "" : request.getRequestURI());
        fields.put("status", status);
        fields.put("exception", ex.getClass().getSimpleName());
        if (StringUtils.hasText(ex.getMessage())) {
            fields.put("message", ex.getMessage());
        }
        String message = formatter.format("http_request_error", fields);
        if (status >= 500) {
            log.error(message, ex);
        } else {
            log.warn(message);
        }
    }

    private HttpStatusCode resolveStatus(Exception ex) {
        if (ex instanceof ErrorResponse errorResponse) {
            return errorResponse.getStatusCode();
        }
        ResponseStatus responseStatus = AnnotatedElementUtils.findMergedAnnotation(ex.getClass(), ResponseStatus.class);
        if (responseStatus != null) {
            HttpStatus annotatedStatus = responseStatus.code() != HttpStatus.INTERNAL_SERVER_ERROR
                    ? responseStatus.code()
                    : responseStatus.value();
            return annotatedStatus;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String resolveClientMessage(Exception ex, int status) {
        if (status >= 500) {
            return "Internal server error";
        }
        if (ex instanceof MissingServletRequestParameterException missingParameter) {
            return "Missing request parameter: " + missingParameter.getParameterName();
        }
        if (ex instanceof MissingServletRequestPartException missingPart) {
            return "Missing request part: " + missingPart.getRequestPartName();
        }
        if (ex instanceof MethodArgumentNotValidException || ex instanceof BindException || ex instanceof MethodArgumentTypeMismatchException) {
            return "Bad request";
        }
        if (ex instanceof HttpMessageNotReadableException) {
            return "Malformed request body";
        }
        if (ex instanceof HttpMediaTypeNotSupportedException) {
            return "Unsupported media type";
        }
        HttpStatus httpStatus = HttpStatus.resolve(status);
        return httpStatus == null ? "Request failed" : httpStatus.getReasonPhrase();
    }

    private String resolveRequestId(@Nullable HttpServletRequest request) {
        if (request != null) {
            Object attribute = request.getAttribute(WebLogConstants.REQUEST_ID_ATTRIBUTE);
            if (attribute instanceof String requestId && StringUtils.hasText(requestId)) {
                return requestId;
            }
        }
        return MDC.get("requestId");
    }

    public record ApiErrorResponse(
            int status,
            String error,
            String requestId,
            String path,
            OffsetDateTime timestamp) {
    }
}
