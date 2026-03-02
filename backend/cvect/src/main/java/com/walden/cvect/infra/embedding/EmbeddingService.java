package com.walden.cvect.infra.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
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
            EmbeddingResponse response = requestNativeEmbedding(texts);
            return validateAndConvert(texts, response);
        } catch (WebClientResponseException.NotFound notFound) {
            String fallbackOpenAiUrl = resolveFallbackOpenAiUrl(config.getServiceUrl());
            if (fallbackOpenAiUrl == null) {
                log.error("Failed to generate embeddings: {}", notFound.getMessage());
                throw new RuntimeException("Embedding generation failed: " + notFound.getMessage(), notFound);
            }
            log.warn("Embedding endpoint {} returned 404, fallback to {}",
                    config.getServiceUrl(), fallbackOpenAiUrl);
            try {
                OpenAiEmbeddingResponse response = requestOpenAiEmbedding(fallbackOpenAiUrl, texts);
                return validateAndConvertOpenAi(texts, response);
            } catch (Exception fallbackEx) {
                log.error("Failed to generate embeddings with OpenAI fallback: {}", fallbackEx.getMessage());
                throw new RuntimeException("Embedding generation failed: " + fallbackEx.getMessage(), fallbackEx);
            }
        } catch (Exception e) {
            log.error("Failed to generate embeddings: {}", e.getMessage());
            throw new RuntimeException("Embedding generation failed: " + e.getMessage(), e);
        }
    }

    private EmbeddingResponse requestNativeEmbedding(List<String> texts) {
        EmbeddingRequest request = new EmbeddingRequest(texts);
        return webClient.post()
                .bodyValue(request)
                .retrieve()
                .bodyToMono(EmbeddingResponse.class)
                .timeout(requestTimeout)
                .block();
    }

    private OpenAiEmbeddingResponse requestOpenAiEmbedding(String url, List<String> texts) {
        OpenAiEmbeddingRequest request = new OpenAiEmbeddingRequest(config.getModelName(), texts);
        return WebClient.builder()
                .baseUrl(url)
                .build()
                .post()
                .bodyValue(request)
                .retrieve()
                .bodyToMono(OpenAiEmbeddingResponse.class)
                .timeout(requestTimeout)
                .block();
    }

    private List<float[]> validateAndConvert(List<String> texts, EmbeddingResponse response) {
        if (response == null || response.embeddings() == null) {
            throw new IllegalStateException("Embedding service returned empty response");
        }
        if (response.embeddings().size() != texts.size()) {
            throw new IllegalStateException("Embedding response size mismatch");
        }
        for (List<Float> vector : response.embeddings()) {
            validateVectorDimension(vector);
        }
        log.info("Generated {} embeddings", response.embeddings().size());
        return toFloatArrays(response.embeddings());
    }

    private List<float[]> validateAndConvertOpenAi(List<String> texts, OpenAiEmbeddingResponse response) {
        if (response == null || response.data() == null) {
            throw new IllegalStateException("OpenAI embedding service returned empty response");
        }
        if (response.data().size() != texts.size()) {
            throw new IllegalStateException("OpenAI embedding response size mismatch");
        }
        List<List<Float>> vectors = response.data().stream().map(OpenAiEmbeddingData::embedding).toList();
        for (List<Float> vector : vectors) {
            validateVectorDimension(vector);
        }
        log.info("Generated {} embeddings (openai-compatible)", vectors.size());
        return toFloatArrays(vectors);
    }

    private void validateVectorDimension(List<Float> vector) {
        if (vector == null || vector.size() != config.getDimension()) {
            throw new IllegalStateException(
                    "Embedding dimension mismatch, expected=" + config.getDimension()
                            + ", actual=" + (vector == null ? 0 : vector.size()));
        }
    }

    private List<float[]> toFloatArrays(List<List<Float>> vectors) {
        return vectors.stream()
                .map(list -> {
                    float[] arr = new float[list.size()];
                    for (int i = 0; i < list.size(); i++) {
                        arr[i] = list.get(i);
                    }
                    return arr;
                })
                .toList();
    }

    private String resolveFallbackOpenAiUrl(String rawServiceUrl) {
        try {
            URI uri = URI.create(rawServiceUrl);
            String path = uri.getPath();
            if (path == null || !path.endsWith("/embed")) {
                return null;
            }
            String fallbackPath = path.substring(0, path.length() - "/embed".length()) + "/v1/embeddings";
            URI fallbackUri = new URI(
                    uri.getScheme(),
                    uri.getUserInfo(),
                    uri.getHost(),
                    uri.getPort(),
                    fallbackPath,
                    null,
                    null);
            return fallbackUri.toString();
        } catch (Exception ex) {
            return null;
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

    public record OpenAiEmbeddingRequest(String model, List<String> input) {
    }

    public record OpenAiEmbeddingData(List<Float> embedding) {
    }

    public record OpenAiEmbeddingResponse(List<OpenAiEmbeddingData> data) {
    }
}
