package com.walden.cvect.web.controller.upload;

import com.walden.cvect.web.sse.BatchStreamService;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * 批次级 SSE 订阅
 */
@RestController
public class BatchSseController {

    private static final Logger log = LoggerFactory.getLogger(BatchSseController.class);
    private static final String LEGACY_PATH_PREFIX = "/api/uploads/batches/";
    private static final String LEGACY_PATH_SUFFIX = "/stream";

    private final BatchStreamService batchStreamService;
    private final MeterRegistry meterRegistry;

    public BatchSseController(BatchStreamService batchStreamService,
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.batchStreamService = batchStreamService;
        this.meterRegistry = meterRegistryProvider.getIfAvailable();
    }

    @GetMapping({
            "/api/sse/batches/{batchId}",
            "/api/uploads/batches/{batchId}/stream"
    })
    public SseEmitter stream(@PathVariable UUID batchId, HttpServletRequest request) {
        recordLegacyPathUsage(request);
        return batchStreamService.subscribe(batchId);
    }

    private void recordLegacyPathUsage(HttpServletRequest request) {
        if (request == null) {
            return;
        }
        String path = request.getRequestURI();
        if (path == null) {
            return;
        }
        if (path.startsWith(LEGACY_PATH_PREFIX) && path.endsWith(LEGACY_PATH_SUFFIX)) {
            if (meterRegistry != null) {
                meterRegistry.counter("cvect.compat.sse_batch_legacy_path").increment();
            }
            log.info("Deprecated SSE batch path used: {}", path);
        }
    }
}
