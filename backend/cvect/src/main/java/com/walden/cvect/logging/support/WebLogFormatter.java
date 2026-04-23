package com.walden.cvect.logging.support;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class WebLogFormatter {

    public String format(String event, Map<String, ?> fields) {
        LinkedHashMap<String, Object> ordered = new LinkedHashMap<>();
        ordered.put("event", event);
        if (fields != null) {
            for (Map.Entry<String, ?> entry : fields.entrySet()) {
                Object value = entry.getValue();
                if (value == null) {
                    continue;
                }
                if (value instanceof String stringValue && !StringUtils.hasText(stringValue)) {
                    continue;
                }
                ordered.put(entry.getKey(), value);
            }
        }

        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Object> entry : ordered.entrySet()) {
            if (!first) {
                sb.append(' ');
            }
            first = false;
            sb.append(entry.getKey()).append('=').append(render(entry.getValue()));
        }
        return sb.toString();
    }

    private String render(Object value) {
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        String text = String.valueOf(value);
        if (text.isEmpty()) {
            return "\"\"";
        }
        if (requiresQuoting(text)) {
            return '"' + LogTextEscaper.escape(text) + '"';
        }
        return text;
    }

    private boolean requiresQuoting(String text) {
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isWhitespace(ch) || Character.isISOControl(ch) || ch == '=' || ch == ',' || ch == '"' || ch == '[' || ch == ']' || ch == '{' || ch == '}') {
                return true;
            }
        }
        return false;
    }
}
