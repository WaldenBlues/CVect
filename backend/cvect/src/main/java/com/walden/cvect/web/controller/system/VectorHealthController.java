package com.walden.cvect.web.controller.system;

import com.walden.cvect.infra.embedding.EmbeddingConfig;
import com.walden.cvect.infra.vector.VectorStoreService;
import com.walden.cvect.logging.aop.AuditAction;
import com.walden.cvect.model.entity.vector.VectorIngestTaskStatus;
import com.walden.cvect.repository.VectorIngestTaskJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/vector")
public class VectorHealthController {
    private static final String DEFAULT_EMBEDDING_HEALTH_URL = "http://localhost:8001/ready";

    private final VectorIngestTaskJpaRepository taskRepository;
    private final EmbeddingConfig embeddingConfig;
    private final VectorStoreService vectorStoreService;
    private final WebClient webClient;
    private final boolean vectorEnabled;
    private final boolean workerEnabled;

    public VectorHealthController(
            VectorIngestTaskJpaRepository taskRepository,
            EmbeddingConfig embeddingConfig,
            VectorStoreService vectorStoreService,
            @Value("${app.vector.enabled:true}") boolean vectorEnabled,
            @Value("${app.vector.ingest.worker.enabled:true}") boolean workerEnabled) {
        this.taskRepository = taskRepository;
        this.embeddingConfig = embeddingConfig;
        this.vectorStoreService = vectorStoreService;
        this.webClient = WebClient.builder().build();
        this.vectorEnabled = vectorEnabled;
        this.workerEnabled = workerEnabled;
    }

    @GetMapping("/health")
    @AuditAction(action = "check_vector_health", target = "vector", logResult = true)
    public ResponseEntity<VectorHealthResponse> health() {
        long pending = countByStatus(VectorIngestTaskStatus.PENDING);
        long processing = countByStatus(VectorIngestTaskStatus.PROCESSING);
        long done = countByStatus(VectorIngestTaskStatus.DONE);
        long failed = countByStatus(VectorIngestTaskStatus.FAILED);

        if (!vectorEnabled) {
            return ResponseEntity.ok(new VectorHealthResponse(
                    "DISABLED",
                    false,
                    workerEnabled,
                    false,
                    null,
                    "Vector store disabled by configuration",
                    false,
                    "Vector store disabled by configuration",
                    pending,
                    processing,
                    done,
                    failed,
                    LocalDateTime.now()));
        }
        boolean vectorStoreOperational = vectorStoreService.isOperational();
        String vectorStoreError = vectorStoreService.getAvailabilityMessage();
        if (!workerEnabled) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new VectorHealthResponse(
                    "DEGRADED",
                    true,
                    false,
                    false,
                    null,
                    "Vector ingest worker disabled by configuration",
                    vectorStoreOperational,
                    vectorStoreError,
                    pending,
                    processing,
                    done,
                    failed,
                    LocalDateTime.now()));
        }

        EmbeddingHealth embeddingHealth = checkEmbeddingHealth(embeddingConfig.getServiceUrl());
        boolean healthy = vectorStoreOperational && embeddingHealth.reachable;
        String status = healthy ? "UP" : "DEGRADED";

        return ResponseEntity.status(healthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(new VectorHealthResponse(
                status,
                vectorEnabled,
                workerEnabled,
                embeddingHealth.reachable,
                embeddingHealth.healthUrl,
                embeddingHealth.error,
                vectorStoreOperational,
                vectorStoreError,
                pending,
                processing,
                done,
                failed,
                LocalDateTime.now()));
    }

    private EmbeddingHealth checkEmbeddingHealth(String embeddingServiceUrl) {
        String healthUrl = resolveHealthUrl(embeddingConfig.getHealthUrl(), embeddingServiceUrl);
        try {
            webClient.get()
                    .uri(healthUrl)
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofSeconds(Math.max(1, embeddingConfig.getTimeoutSeconds())))
                    .block();
            return new EmbeddingHealth(true, healthUrl, null);
        } catch (Exception ex) {
            return new EmbeddingHealth(false, healthUrl, ex.getMessage());
        }
    }

    private String resolveHealthUrl(String configuredHealthUrl, String embeddingServiceUrl) {
        if (configuredHealthUrl != null && !configuredHealthUrl.isBlank()) {
            return configuredHealthUrl;
        }
        try {
            URI uri = URI.create(embeddingServiceUrl);
            String path = uri.getPath();
            if (path == null || path.isBlank() || "/".equals(path)) {
                path = "/ready";
            } else if (path.endsWith("/v1/embeddings") || path.endsWith("/embeddings")) {
                path = "/ready";
            } else if (path.endsWith("/embedding")) {
                path = path.substring(0, path.length() - "/embedding".length()) + "/ready";
            } else if (path.endsWith("/embed")) {
                path = path.substring(0, path.length() - "/embed".length()) + "/ready";
            } else {
                path = path.replaceAll("/+$", "") + "/ready";
            }
            URI healthUri = new URI(
                    uri.getScheme(),
                    uri.getUserInfo(),
                    uri.getHost(),
                    uri.getPort(),
                    path,
                    null,
                    null);
            return healthUri.toString();
        } catch (Exception ex) {
            return DEFAULT_EMBEDDING_HEALTH_URL;
        }
    }

    private long countByStatus(VectorIngestTaskStatus status) {
        return taskRepository.countByStatusIn(List.of(status));
    }

    private record EmbeddingHealth(
            boolean reachable,
            String healthUrl,
            String error
    ) {
    }

    public record VectorHealthResponse(
            String status,
            boolean vectorEnabled,
            boolean workerEnabled,
            boolean embeddingReachable,
            String embeddingHealthUrl,
            String embeddingError,
            boolean vectorStoreOperational,
            String vectorStoreError,
            long pendingCount,
            long processingCount,
            long doneCount,
            long failedCount,
            LocalDateTime checkedAt
    ) {
    }
}
