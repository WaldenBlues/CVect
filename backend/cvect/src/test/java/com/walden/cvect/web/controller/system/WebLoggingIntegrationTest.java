package com.walden.cvect.web.controller.system;

import com.walden.cvect.logging.aop.AuditAction;
import com.walden.cvect.logging.aop.AppLog;
import com.walden.cvect.logging.aop.TimedAction;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.upload.worker.enabled=false",
        "app.vector.ingest.worker.enabled=false"
})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@Import({
        WebLoggingIntegrationTest.LoggingTestController.class,
        WebLoggingIntegrationTest.LoggingTestService.class
})
class WebLoggingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void shouldPropagateRequestIdAndUseSummaryArguments(CapturedOutput output) throws Exception {
        UUID jdId = UUID.randomUUID();
        String rawContentMarker = "THIS_RAW_CONTENT_SHOULD_NOT_APPEAR_IN_LOGS";
        double initialHttpTimed = timerCount("test.logging.http");
        double initialServiceTimed = timerCount("test.logging.service");

        String responseRequestId = mockMvc.perform(post("/api/test/logging/echo")
                        .queryParam("jdId", jdId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Senior Engineer",
                                  "content": "%s"
                                }
                                """.formatted(rawContentMarker.repeat(8))))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.ok").value(true))
                .andReturn()
                .getResponse()
                .getHeader("X-Request-Id");

        assertThat(responseRequestId).isNotBlank();
        assertThat(output).contains(responseRequestId);
        assertThat(output).contains("event=http_request_start");
        assertThat(output).contains("event=controller_invoke");
        assertThat(output).contains("event=service_invoke");
        assertThat(output).contains("action=test_logged_action");
        assertThat(output).contains("event=audit_action");
        assertThat(output).contains("action=test_audit_action");
        assertThat(output).contains("event=http_request_end");
        assertThat(output).contains("content=len=");
        assertThat(output).doesNotContain(rawContentMarker);
        assertThat(output).doesNotContain("plainAction");
        assertThat(timerCount("test.logging.http")).isEqualTo(initialHttpTimed + 1.0d);
        assertThat(timerCount("test.logging.service")).isEqualTo(initialServiceTimed + 1.0d);
    }

    @Test
    void shouldLogHttpErrorOnceFromAdvice(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/api/test/logging/fail"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.error").value("Internal server error"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        assertThat(countOccurrences(output.getOut(), "event=http_request_error")).isEqualTo(1);
        assertThat(output).contains("handler=advice");
        assertThat(output).doesNotContain("handler=filter");
        assertThat(output).contains("event=controller_invoke");
        assertThat(output).contains("status=error");
        assertThat(output).contains("event=service_invoke");
        assertThat(output).contains("action=test_logged_failure");
        assertThat(output).contains("event=http_request_end");
    }

    private int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private double timerCount(String name) {
        var timer = meterRegistry.find(name).tag("outcome", "success").timer();
        return timer == null ? 0.0d : timer.count();
    }

    @RestController
    @RequestMapping("/api/test/logging")
    static class LoggingTestController {

        private final LoggingTestService loggingTestService;

        LoggingTestController(LoggingTestService loggingTestService) {
            this.loggingTestService = loggingTestService;
        }

        @PostMapping("/echo")
        @TimedAction(metric = "test.logging.http", tags = {"layer", "controller"})
        public Map<String, Object> echo(@RequestParam("jdId") UUID jdId, @RequestBody LoggingRequest request) {
            loggingTestService.loggedAction(request.title(), request.content(), jdId);
            loggingTestService.plainAction();
            return Map.of("ok", true);
        }

        @GetMapping("/fail")
        public Map<String, Object> fail() {
            loggingTestService.failAction("boom");
            return Map.of("ok", false);
        }
    }

    @Service
    static class LoggingTestService {

        @AppLog(action = "test_logged_action")
        @AuditAction(action = "test_audit_action", target = "logging")
        @TimedAction(metric = "test.logging.service", tags = {"layer", "service"})
        void loggedAction(String title, String content, UUID jdId) {
            // no-op
        }

        void plainAction() {
            // no-op
        }

        @AppLog(action = "test_logged_failure")
        void failAction(String reason) {
            throw new IllegalStateException("forced failure");
        }
    }

    record LoggingRequest(String title, String content) {
    }
}
