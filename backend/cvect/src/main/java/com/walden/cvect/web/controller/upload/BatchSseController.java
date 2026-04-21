package com.walden.cvect.web.controller.upload;

import com.walden.cvect.repository.UploadBatchJpaRepository;
import com.walden.cvect.security.CurrentUserService;
import com.walden.cvect.security.DataScopeService;
import com.walden.cvect.web.sse.BatchStreamService;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final UploadBatchJpaRepository batchRepository;
    private final CurrentUserService currentUserService;
    private final DataScopeService dataScopeService;

    public BatchSseController(BatchStreamService batchStreamService,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            UploadBatchJpaRepository batchRepository,
            CurrentUserService currentUserService,
            DataScopeService dataScopeService) {
        this.batchStreamService = batchStreamService;
        this.meterRegistry = meterRegistryProvider.getIfAvailable();
        this.batchRepository = batchRepository;
        this.currentUserService = currentUserService;
        this.dataScopeService = dataScopeService;
    }

    @GetMapping({
            "/api/sse/batches/{batchId}",
            "/api/uploads/batches/{batchId}/stream"
    })
    @PreAuthorize("@permissionGuard.has(T(com.walden.cvect.security.PermissionCodes).RESUME_UPLOAD)")
    public SseEmitter stream(@PathVariable UUID batchId, HttpServletRequest request) {
        recordLegacyPathUsage(request);
        if (!canAccessBatch(batchId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "Batch not found");
        }
        return batchStreamService.subscribe(batchId);
    }

    private boolean canAccessBatch(UUID batchId) {
        UUID tenantId = currentUserService.currentTenantId();
        if (dataScopeService.hasTenantWideScope()) {
            return batchRepository.existsByIdAndTenantId(batchId, tenantId);
        }
        UUID userId = dataScopeService.currentUserIdOrNull();
        return userId != null && batchRepository.existsByIdAndTenantIdAndJobDescriptionCreatedByUserId(
                batchId,
                tenantId,
                userId);
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
