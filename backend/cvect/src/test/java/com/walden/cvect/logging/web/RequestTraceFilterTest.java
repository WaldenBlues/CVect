package com.walden.cvect.logging.web;

import com.walden.cvect.logging.config.LogProperties;
import com.walden.cvect.logging.support.WebLogConstants;
import com.walden.cvect.logging.support.WebLogFormatter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class RequestTraceFilterTest {

    @Test
    void shouldLogFilterErrorAndClearMdcOnFailure(CapturedOutput output) {
        LogProperties properties = new LogProperties();
        RequestTraceFilter filter = new RequestTraceFilter(properties, new WebLogFormatter());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test/filter-failure");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain failingChain = (req, res) -> {
            throw new ServletException("filter failure");
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, failingChain))
                .isInstanceOf(ServletException.class)
                .hasMessageContaining("filter failure");

        String requestId = response.getHeader("X-Request-Id");
        assertThat(requestId).isNotBlank();
        assertThat(request.getAttribute(WebLogConstants.REQUEST_ID_ATTRIBUTE)).isEqualTo(requestId);
        assertThat(output).contains("event=http_request_start");
        assertThat(output).contains("event=http_request_error");
        assertThat(output).contains("handler=filter");
        assertThat(output).contains("event=http_request_end");
        assertThat(MDC.get("requestId")).isNull();
    }
}
