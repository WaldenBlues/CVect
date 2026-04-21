package com.walden.cvect.infra.embedding;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("EmbeddingService HTTP compatibility tests")
class EmbeddingServiceHttpServerTest {

    @Test
    @DisplayName("should call OpenAI-compatible embeddings endpoint when configured")
    void shouldUseOpenAiCompatibleEndpoint() {
        AtomicInteger openAiRequests = new AtomicInteger();
        ExchangeFunction exchange = request -> {
            assertEquals("/v1/embeddings", request.url().getPath());
            openAiRequests.incrementAndGet();
            return json(HttpStatus.OK, """
                    {"data":[
                      {"embedding":[1.0,2.0,3.0]},
                      {"embedding":[4.0,5.0,6.0]}
                    ]}
                    """);
        };

        EmbeddingConfig config = newConfig("http://embedding.test/v1/embeddings", "openai");
        config.setBatchSize(2);

        EmbeddingService service = new EmbeddingService(config, webClientFactory(exchange));
        List<float[]> embeddings = service.embedBatch(List.of("alpha", "beta"));

        assertEquals(1, openAiRequests.get());
        assertEquals(2, embeddings.size());
        assertArrayEquals(new float[] {1.0f, 2.0f, 3.0f}, embeddings.get(0));
        assertArrayEquals(new float[] {4.0f, 5.0f, 6.0f}, embeddings.get(1));
    }

    @Test
    @DisplayName("should fallback to OpenAI-compatible endpoint when native endpoint returns 404")
    void shouldFallbackToOpenAiCompatibleEndpoint() {
        AtomicInteger nativeRequests = new AtomicInteger();
        AtomicInteger openAiRequests = new AtomicInteger();
        ExchangeFunction exchange = request -> {
            String path = request.url().getPath();
            if ("/embed".equals(path)) {
                nativeRequests.incrementAndGet();
                return json(HttpStatus.NOT_FOUND, "{\"error\":\"not found\"}");
            }
            if ("/v1/embeddings".equals(path)) {
                openAiRequests.incrementAndGet();
                return json(HttpStatus.OK, """
                        {"data":[
                          {"embedding":[7.0,8.0,9.0]}
                        ]}
                        """);
            }
            return json(HttpStatus.INTERNAL_SERVER_ERROR, "{\"error\":\"unexpected path\"}");
        };

        EmbeddingConfig config = newConfig("http://embedding.test/embed", "auto");

        EmbeddingService service = new EmbeddingService(config, webClientFactory(exchange));
        List<float[]> embeddings = service.embedBatch(List.of("alpha"));

        assertEquals(1, nativeRequests.get());
        assertEquals(1, openAiRequests.get());
        assertEquals(1, embeddings.size());
        assertArrayEquals(new float[] {7.0f, 8.0f, 9.0f}, embeddings.get(0));
    }

    @Test
    @DisplayName("should call llama.cpp native embedding endpoint when configured")
    void shouldUseLlamaCppEmbeddingEndpoint() {
        AtomicInteger llamaRequests = new AtomicInteger();
        ExchangeFunction exchange = request -> {
            assertEquals("/embedding", request.url().getPath());
            llamaRequests.incrementAndGet();
            return json(HttpStatus.OK, """
                    {"embedding":[0.1,0.2,0.3]}
                    """);
        };

        EmbeddingConfig config = newConfig("http://embedding.test/embedding", "llama_cpp");

        EmbeddingService service = new EmbeddingService(config, webClientFactory(exchange));
        List<float[]> embeddings = service.embedBatch(List.of("alpha"));

        assertEquals(1, llamaRequests.get());
        assertEquals(1, embeddings.size());
        assertArrayEquals(new float[] {0.1f, 0.2f, 0.3f}, embeddings.get(0), 0.0001f);
    }

    @Test
    @DisplayName("should partition embedding requests using configured batch size")
    void shouldPartitionRequestsByConfiguredBatchSize() {
        AtomicInteger openAiRequests = new AtomicInteger();
        ExchangeFunction exchange = request -> {
            assertEquals("/v1/embeddings", request.url().getPath());
            openAiRequests.incrementAndGet();
            return json(HttpStatus.OK, """
                    {"data":[{"embedding":[1.0,2.0,3.0]}]}
                    """);
        };

        EmbeddingConfig config = newConfig("http://embedding.test/v1/embeddings", "openai");
        config.setBatchSize(1);

        EmbeddingService service = new EmbeddingService(config, webClientFactory(exchange));
        List<float[]> embeddings = service.embedBatch(List.of("alpha", "beta"));

        assertEquals(2, openAiRequests.get());
        assertEquals(2, embeddings.size());
    }

    private static EmbeddingConfig newConfig(String serviceUrl, String apiFormat) {
        EmbeddingConfig config = new EmbeddingConfig();
        config.setModelName("test-model");
        config.setServiceUrl(serviceUrl);
        config.setApiFormat(apiFormat);
        config.setDimension(3);
        config.setTimeoutSeconds(5);
        return config;
    }

    private static Function<String, WebClient> webClientFactory(ExchangeFunction exchange) {
        return serviceUrl -> WebClient.builder()
                .baseUrl(serviceUrl)
                .exchangeFunction(exchange)
                .build();
    }

    private static Mono<ClientResponse> json(HttpStatus status, String body) {
        return Mono.just(ClientResponse.create(status)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
    }

}
