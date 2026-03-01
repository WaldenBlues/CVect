package com.walden.cvect.infra.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

/**
 * Embedding 服务 - 调用 Python embedding 服务
 *
 * 设计说明: 由于 Java 生态缺少成熟的本地 embedding 推理库，
 * 采用 HTTP 调用 Python embedding 服务的架构。
 *
 * Python 服务使用 sentence-transformers 或 transformers 库加载 Qwen 模型，
 * 提供 REST API: POST /embed {texts: [...]} → {embeddings: [[...], ...]}
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final WebClient webClient;
    private final EmbeddingConfig config;
    private final Duration requestTimeout;

    public EmbeddingService(EmbeddingConfig config) {
        this.config = config;
        this.webClient = WebClient.builder()
                .baseUrl(config.getServiceUrl())
                .build();
        this.requestTimeout = Duration.ofSeconds(Math.max(1, config.getTimeoutSeconds()));
        log.info("EmbeddingService initialized with model: {}", config.getModelName());
        log.info("Connecting to embedding service at: {}", config.getServiceUrl());
    }

    /**
     * 生成单个文本的 embedding 向量
     */
    public float[] embed(String text) {
        List<float[]> results = embedBatch(List.of(text));
        return results.isEmpty() ? new float[0] : results.get(0);
    }

    /**
     * 批量生成 embedding 向量
     * 使用 WebClient 调用 Python embedding 服务
     */
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        try {
            EmbeddingRequest request = new EmbeddingRequest(texts);

            EmbeddingResponse response = webClient.post()
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(EmbeddingResponse.class)
                    .timeout(requestTimeout)
                    .block();

            if (response == null || response.embeddings() == null) {
                throw new IllegalStateException("Embedding service returned empty response");
            }
            if (response.embeddings().size() != texts.size()) {
                throw new IllegalStateException("Embedding response size mismatch");
            }
            for (List<Float> vector : response.embeddings()) {
                if (vector == null || vector.size() != config.getDimension()) {
                    throw new IllegalStateException(
                            "Embedding dimension mismatch, expected=" + config.getDimension()
                                    + ", actual=" + (vector == null ? 0 : vector.size()));
                }
            }
            log.info("Generated {} embeddings", response.embeddings().size());
            // 转换 List<Float> -> float[]
            return response.embeddings().stream()
                    .map(list -> {
                        float[] arr = new float[list.size()];
                        for (int i = 0; i < list.size(); i++) {
                            arr[i] = list.get(i);
                        }
                        return arr;
                    })
                    .toList();

        } catch (Exception e) {
            log.error("Failed to generate embeddings: {}", e.getMessage());
            throw new RuntimeException("Embedding generation failed", e);
        }
    }

    public int getDimension() {
        return config.getDimension();
    }

    // Request/Response DTOs
    public record EmbeddingRequest(List<String> texts) {
    }

    public record EmbeddingResponse(List<List<Float>> embeddings) {
    }
}
