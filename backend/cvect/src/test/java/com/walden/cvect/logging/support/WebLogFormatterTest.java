package com.walden.cvect.logging.support;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class WebLogFormatterTest {

    private final WebLogFormatter formatter = new WebLogFormatter();

    @Test
    void escapesControlCharactersAndPreservesExistingQuoting() {
        LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
        fields.put("message", "line1\nline2\r\t\u0001\\path\"quote");

        String formatted = formatter.format("http_request", fields);

        assertThat(formatted).isEqualTo(
                "event=http_request message=\"line1\\nline2\\r\\t\\u0001\\\\path\\\"quote\"");
    }
}
