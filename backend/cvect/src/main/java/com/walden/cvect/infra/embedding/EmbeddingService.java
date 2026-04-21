package com.walden.cvect.infra.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

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
    private static final String API_FORMAT_AUTO = "auto";
    private static final String API_FORMAT_NATIVE = "native";
    private static final String API_FORMAT_OPENAI = "openai";
    private static final String API_FORMAT_LLAMA_CPP = "llama_cpp";

    private final WebClient webClient;
    private final Function<String, WebClient> webClientFactory;
    private final EmbeddingConfig config;
    private final Duration requestTimeout;

    @Autowired
    public EmbeddingService(EmbeddingConfig config) {
        this(config, serviceUrl -> WebClient.builder().baseUrl(serviceUrl).build());
    }

    EmbeddingService(EmbeddingConfig config, Function<String, WebClient> webClientFactory) {
        this.config = config;
        this.webClientFactory = webClientFactory;
        this.webClient = webClientFactory.apply(config.getServiceUrl());
        this.requestTimeout = Duration.ofSeconds(Math.max(1, config.getTimeoutSeconds()));
        log.info("EmbeddingService initialized with model: {}", config.getModelName());
        log.info("Connecting to embedding service at: {}", config.getServiceUrl());
        log.info("Embedding API format: {}", normalizeApiFormat(config.getApiFormat()));
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

        String apiFormat = normalizeApiFormat(config.getApiFormat());
        int batchSize = Math.max(1, config.getBatchSize());
        List<float[]> results = new ArrayList<>(texts.size());
        try {
            for (int start = 0; start < texts.size(); start += batchSize) {
                List<String> batch = texts.subList(start, Math.min(texts.size(), start + batchSize));
                results.addAll(requestEmbeddings(batch, apiFormat));
            }
            return List.copyOf(results);
        } catch (WebClientResponseException.NotFound notFound) {
            if (API_FORMAT_OPENAI.equals(apiFormat) || API_FORMAT_LLAMA_CPP.equals(apiFormat)) {
                log.error("Failed to generate embeddings: {}", notFound.getMessage());
                throw new RuntimeException("Embedding generation failed: " + notFound.getMessage(), notFound);
            }
            String fallbackOpenAiUrl = resolveFallbackOpenAiUrl(config.getServiceUrl(), apiFormat);
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

    private List<float[]> requestEmbeddings(List<String> texts, String apiFormat) {
        return switch (apiFormat) {
            case API_FORMAT_NATIVE -> requestNativeEmbeddings(texts);
            case API_FORMAT_OPENAI -> requestOpenAiEmbeddings(config.getServiceUrl(), texts);
            case API_FORMAT_LLAMA_CPP -> requestLlamaCppEmbeddings(config.getServiceUrl(), texts);
            default -> requestAutoDetectedEmbeddings(texts);
        };
    }

    private List<float[]> requestNativeEmbeddings(List<String> texts) {
        EmbeddingResponse response = requestNativeEmbedding(texts);
        return validateAndConvert(texts, response);
    }

    private List<float[]> requestOpenAiEmbeddings(String url, List<String> texts) {
        OpenAiEmbeddingResponse response = requestOpenAiEmbedding(url, texts);
        return validateAndConvertOpenAi(texts, response);
    }

    private List<float[]> requestAutoDetectedEmbeddings(List<String> texts) {
        if (looksLikeOpenAiEndpoint(config.getServiceUrl())) {
            return requestOpenAiEmbeddings(config.getServiceUrl(), texts);
        }
        if (looksLikeLlamaCppEndpoint(config.getServiceUrl())) {
            return requestLlamaCppEmbeddings(config.getServiceUrl(), texts);
        }
        return requestNativeEmbeddings(texts);
    }

    private List<float[]> requestLlamaCppEmbeddings(String url, List<String> texts) {
        List<float[]> embeddings = new ArrayList<>(texts.size());
        for (String text : texts) {
            LlamaCppEmbeddingResponse response = requestLlamaCppEmbedding(url, text);
            embeddings.add(validateAndConvertLlamaCpp(response));
        }
        log.info("Generated {} embeddings (llama.cpp)", embeddings.size());
        return embeddings;
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
        return webClientFactory.apply(url)
                .post()
                .bodyValue(request)
                .retrieve()
                .bodyToMono(OpenAiEmbeddingResponse.class)
                .timeout(requestTimeout)
                .block();
    }

    private LlamaCppEmbeddingResponse requestLlamaCppEmbedding(String url, String text) {
        LlamaCppEmbeddingRequest request = new LlamaCppEmbeddingRequest(text);
        return webClientFactory.apply(url)
                .post()
                .bodyValue(request)
                .retrieve()
                .bodyToMono(LlamaCppEmbeddingResponse.class)
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

    private float[] validateAndConvertLlamaCpp(LlamaCppEmbeddingResponse response) {
        if (response == null || response.embedding() == null) {
            throw new IllegalStateException("llama.cpp embedding service returned empty response");
        }
        validateVectorDimension(response.embedding());
        float[] vector = new float[response.embedding().size()];
        for (int i = 0; i < response.embedding().size(); i++) {
            vector[i] = response.embedding().get(i);
        }
        return vector;
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

    private String resolveFallbackOpenAiUrl(String rawServiceUrl, String apiFormat) {
        if (API_FORMAT_OPENAI.equals(apiFormat) || looksLikeOpenAiEndpoint(rawServiceUrl)) {
            return null;
        }
        try {
            URI uri = URI.create(rawServiceUrl);
            String path = uri.getPath();
            if (path == null) {
                return null;
            }
            String fallbackPath;
            if (path.endsWith("/embed")) {
                fallbackPath = path.substring(0, path.length() - "/embed".length()) + "/v1/embeddings";
            } else if (path.endsWith("/embedding")) {
                fallbackPath = path.substring(0, path.length() - "/embedding".length()) + "/v1/embeddings";
            } else {
                return null;
            }
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

    private boolean looksLikeOpenAiEndpoint(String rawServiceUrl) {
        try {
            URI uri = URI.create(rawServiceUrl);
            String path = uri.getPath();
            if (path == null) {
                return false;
            }
            String normalizedPath = path.replaceAll("/+$", "");
            return normalizedPath.endsWith("/v1/embeddings") || normalizedPath.endsWith("/embeddings");
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean looksLikeLlamaCppEndpoint(String rawServiceUrl) {
        try {
            URI uri = URI.create(rawServiceUrl);
            String path = uri.getPath();
            if (path == null) {
                return false;
            }
            return path.replaceAll("/+$", "").endsWith("/embedding");
        } catch (Exception ex) {
            return false;
        }
    }

    private String normalizeApiFormat(String rawApiFormat) {
        if (rawApiFormat == null || rawApiFormat.isBlank()) {
            return API_FORMAT_AUTO;
        }
        String normalized = rawApiFormat.trim().toLowerCase();
        if ("openai-compatible".equals(normalized)) {
            return API_FORMAT_OPENAI;
        }
        if ("llama-cpp".equals(normalized) || "llama.cpp".equals(normalized)) {
            return API_FORMAT_LLAMA_CPP;
        }
        if (API_FORMAT_NATIVE.equals(normalized)
                || API_FORMAT_OPENAI.equals(normalized)
                || API_FORMAT_LLAMA_CPP.equals(normalized)) {
            return normalized;
        }
        return API_FORMAT_AUTO;
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

    public record LlamaCppEmbeddingRequest(String content) {
    }

    public record LlamaCppEmbeddingResponse(List<Float> embedding) {
    }
}
