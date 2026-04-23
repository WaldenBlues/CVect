package com.walden.cvect.logging.support;

import com.walden.cvect.logging.config.LogProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.file.Path;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class LogValueSanitizer {

    private final LogProperties properties;
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public LogValueSanitizer(LogProperties properties) {
        this.properties = properties;
    }

    public String summarizeMethodArguments(Method method, Object[] args) {
        if (method == null || args == null || args.length == 0) {
            return "";
        }
        String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String name = parameterNames != null && i < parameterNames.length && StringUtils.hasText(parameterNames[i])
                    ? parameterNames[i]
                    : "arg" + i;
            String summary = summarizeNamedValue(name, args[i]);
            if (!StringUtils.hasText(summary)) {
                continue;
            }
            parts.add(name + "=" + summary);
        }
        return String.join(",", parts);
    }

    public String summarizeReturnValue(Object value) {
        return summarizeNamedValue("result", value);
    }

    public String summarizeExceptionMessage(Throwable throwable) {
        if (throwable == null || !StringUtils.hasText(throwable.getMessage())) {
            return "";
        }
        String message = throwable.getMessage().trim();
        if (message.length() > properties.getMaxStringLength()) {
            return "len=" + message.length();
        }
        return message;
    }

    private String summarizeNamedValue(String name, Object value) {
        if (value == null) {
            return "null";
        }
        if (shouldOmit(value)) {
            return null;
        }
        if (looksSensitive(name)) {
            return "<redacted>";
        }
        if (value instanceof MultipartFile multipartFile) {
            return summarizeMultipartFile(multipartFile);
        }
        if (value instanceof MultipartFile[] multipartFiles) {
            return summarizeMultipartFiles(Arrays.asList(multipartFiles));
        }
        if (value instanceof String stringValue) {
            return summarizeString(name, stringValue);
        }
        if (value instanceof UUID || value instanceof Number || value instanceof Boolean || value instanceof Enum<?> || value instanceof TemporalAccessor) {
            return String.valueOf(value);
        }
        if (value instanceof Pageable pageable) {
            return "Pageable{page=%d,size=%d,sort=%s}".formatted(
                    pageable.getPageNumber(),
                    pageable.getPageSize(),
                    pageable.getSort());
        }
        if (value instanceof Optional<?> optional) {
            return optional.map(item -> "Optional[" + summarizeNamedValue(name, item) + "]")
                    .orElse("Optional.empty");
        }
        if (value instanceof ResponseEntity<?> responseEntity) {
            return "ResponseEntity{status=%d}".formatted(responseEntity.getStatusCode().value());
        }
        if (value instanceof Path path) {
            return "Path{name=%s}".formatted(quote(path.getFileName() == null ? path.toString() : path.getFileName().toString()));
        }
        if (value instanceof Map<?, ?> map) {
            return summarizeMap(map);
        }
        if (value instanceof Collection<?> collection) {
            return "Collection[size=%d]".formatted(collection.size());
        }
        if (value instanceof byte[] bytes) {
            return "byte[%d]".formatted(bytes.length);
        }
        if (value instanceof float[] floats) {
            return "float[%d]".formatted(floats.length);
        }
        if (value instanceof Object[] array) {
            return "%s[%d]".formatted(value.getClass().getComponentType() == null
                    ? "Object"
                    : value.getClass().getComponentType().getSimpleName(), array.length);
        }
        if (value.getClass().isRecord()) {
            return summarizeRecord(value);
        }
        if (ClassUtils.isPrimitiveOrWrapper(value.getClass())) {
            return String.valueOf(value);
        }
        return value.getClass().getSimpleName();
    }

    private boolean shouldOmit(Object value) {
        return value instanceof HttpServletRequest
                || value instanceof HttpServletResponse
                || value instanceof BindingResult
                || value instanceof InputStream
                || value instanceof OutputStream
                || value instanceof SseEmitter;
    }

    private boolean looksSensitive(String name) {
        String normalized = normalizeName(name);
        return normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("token")
                || normalized.contains("authorization");
    }

    private boolean looksLongText(String name) {
        String normalized = normalizeName(name);
        return normalized.contains("content")
                || normalized.contains("text")
                || normalized.contains("body")
                || normalized.contains("query")
                || normalized.contains("resume")
                || normalized.contains("description");
    }

    private String summarizeString(String name, String value) {
        if (!StringUtils.hasText(value)) {
            return "\"\"";
        }
        if (looksLongText(name)) {
            return "len=" + value.length();
        }
        String trimmed = value.trim();
        if (trimmed.length() > properties.getMaxStringLength()) {
            trimmed = trimmed.substring(0, properties.getMaxStringLength()) + "...";
        }
        return quote(trimmed);
    }

    private String summarizeMultipartFile(MultipartFile multipartFile) {
        return "MultipartFile{name=%s,size=%d,contentType=%s}".formatted(
                quote(Objects.toString(multipartFile.getOriginalFilename(), "")),
                multipartFile.getSize(),
                quote(Objects.toString(multipartFile.getContentType(), "")));
    }

    private String summarizeMultipartFiles(List<MultipartFile> files) {
        long totalBytes = 0L;
        List<String> names = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null) {
                continue;
            }
            totalBytes += file.getSize();
            if (StringUtils.hasText(file.getOriginalFilename()) && names.size() < 3) {
                names.add(file.getOriginalFilename());
            }
        }
        String namesPart = names.isEmpty() ? "" : ",names=" + quote(String.join("|", names));
        return "MultipartFile[%d]{totalBytes=%d%s}".formatted(files.size(), totalBytes, namesPart);
    }

    private String summarizeMap(Map<?, ?> map) {
        List<String> keys = new ArrayList<>();
        for (Object key : map.keySet()) {
            if (keys.size() >= 5) {
                break;
            }
            keys.add(String.valueOf(key));
        }
        String keysPart = keys.isEmpty() ? "" : ",keys=" + quote(String.join("|", keys));
        return "Map[size=%d%s]".formatted(map.size(), keysPart);
    }

    private String summarizeRecord(Object value) {
        RecordComponent[] components = value.getClass().getRecordComponents();
        if (components == null || components.length == 0) {
            return value.getClass().getSimpleName();
        }
        List<String> parts = new ArrayList<>();
        for (RecordComponent component : components) {
            try {
                Method accessor = component.getAccessor();
                if (!accessor.canAccess(value)) {
                    accessor.setAccessible(true);
                }
                Object nestedValue = accessor.invoke(value);
                String summary = summarizeNamedValue(component.getName(), nestedValue);
                if (!StringUtils.hasText(summary)) {
                    continue;
                }
                parts.add(component.getName() + "=" + summary);
            } catch (Exception ignored) {
                parts.add(component.getName() + "=<unavailable>");
            }
        }
        return value.getClass().getSimpleName() + "{" + String.join(",", parts) + "}";
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private String quote(String value) {
        return '"' + LogTextEscaper.escape(value) + '"';
    }
}
