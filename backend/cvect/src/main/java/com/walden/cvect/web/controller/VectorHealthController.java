package com.walden.cvect.web.controller;

import com.walden.cvect.infra.embedding.EmbeddingConfig;
import com.walden.cvect.model.entity.vector.VectorIngestTaskStatus;
import com.walden.cvect.repository.VectorIngestTaskJpaRepository;
import org.springframework.beans.factory.annotation.Value;
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

    private final VectorIngestTaskJpaRepository taskRepository;
    private final EmbeddingConfig embeddingConfig;
    private final WebClient webClient;
    private final boolean vectorEnabled;
    private final boolean workerEnabled;

    public VectorHealthController(
            VectorIngestTaskJpaRepository taskRepository,
            EmbeddingConfig embeddingConfig,
            @Value("${app.vector.enabled:true}") boolean vectorEnabled,
            @Value("${app.vector.ingest.worker.enabled:true}") boolean workerEnabled) {
        this.taskRepository = taskRepository;
        this.embeddingConfig = embeddingConfig;
        this.webClient = WebClient.builder().build();
        this.vectorEnabled = vectorEnabled;
        this.workerEnabled = workerEnabled;
    }

    @GetMapping("/health")
    public ResponseEntity<VectorHealthResponse> health() {
        long pending = taskRepository.countByStatusIn(List.of(VectorIngestTaskStatus.PENDING));
        long processing = taskRepository.countByStatusIn(List.of(VectorIngestTaskStatus.PROCESSING));
        long done = taskRepository.countByStatusIn(List.of(VectorIngestTaskStatus.DONE));
        long failed = taskRepository.countByStatusIn(List.of(VectorIngestTaskStatus.FAILED));

        EmbeddingHealth embeddingHealth = checkEmbeddingHealth(embeddingConfig.getServiceUrl());
        String status = embeddingHealth.reachable ? "UP" : "DEGRADED";

        return ResponseEntity.ok(new VectorHealthResponse(
                status,
                vectorEnabled,
                workerEnabled,
                embeddingHealth.reachable,
                embeddingHealth.healthUrl,
                embeddingHealth.error,
                pending,
                processing,
                done,
                failed,
                LocalDateTime.now()));
    }

    private EmbeddingHealth checkEmbeddingHealth(String embeddingServiceUrl) {
        String healthUrl = resolveHealthUrl(embeddingServiceUrl);
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

    private String resolveHealthUrl(String embeddingServiceUrl) {
        try {
            URI uri = URI.create(embeddingServiceUrl);
            String path = uri.getPath();
            if (path == null || path.isBlank() || "/".equals(path)) {
                path = "/health";
            } else if (path.endsWith("/embed")) {
                path = path.substring(0, path.length() - "/embed".length()) + "/health";
            } else {
                path = path.replaceAll("/+$", "") + "/health";
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
            return "http://localhost:8001/health";
        }
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
            long pendingCount,
            long processingCount,
            long doneCount,
            long failedCount,
            LocalDateTime checkedAt
    ) {
    }
}
