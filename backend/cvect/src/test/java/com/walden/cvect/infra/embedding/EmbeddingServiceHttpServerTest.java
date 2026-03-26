package com.walden.cvect.infra.embedding;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("EmbeddingService HTTP compatibility tests")
class EmbeddingServiceHttpServerTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("should call OpenAI-compatible embeddings endpoint when configured")
    void shouldUseOpenAiCompatibleEndpoint() throws Exception {
        AtomicInteger openAiRequests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/embeddings", exchange -> {
            openAiRequests.incrementAndGet();
            drain(exchange.getRequestBody());
            writeJson(exchange, 200, """
                    {"data":[
                      {"embedding":[1.0,2.0,3.0]},
                      {"embedding":[4.0,5.0,6.0]}
                    ]}
                    """);
        });
        server.start();

        EmbeddingConfig config = new EmbeddingConfig();
        config.setModelName("test-model");
        config.setServiceUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/embeddings");
        config.setApiFormat("openai");
        config.setBatchSize(2);
        config.setDimension(3);
        config.setTimeoutSeconds(5);

        EmbeddingService service = new EmbeddingService(config);
        List<float[]> embeddings = service.embedBatch(List.of("alpha", "beta"));

        assertEquals(1, openAiRequests.get());
        assertEquals(2, embeddings.size());
        assertArrayEquals(new float[] {1.0f, 2.0f, 3.0f}, embeddings.get(0));
        assertArrayEquals(new float[] {4.0f, 5.0f, 6.0f}, embeddings.get(1));
    }

    @Test
    @DisplayName("should fallback to OpenAI-compatible endpoint when native endpoint returns 404")
    void shouldFallbackToOpenAiCompatibleEndpoint() throws Exception {
        AtomicInteger nativeRequests = new AtomicInteger();
        AtomicInteger openAiRequests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/embed", exchange -> {
            nativeRequests.incrementAndGet();
            drain(exchange.getRequestBody());
            writeJson(exchange, 404, "{\"error\":\"not found\"}");
        });
        server.createContext("/v1/embeddings", exchange -> {
            openAiRequests.incrementAndGet();
            drain(exchange.getRequestBody());
            writeJson(exchange, 200, """
                    {"data":[
                      {"embedding":[7.0,8.0,9.0]}
                    ]}
                    """);
        });
        server.start();

        EmbeddingConfig config = new EmbeddingConfig();
        config.setModelName("test-model");
        config.setServiceUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/embed");
        config.setApiFormat("auto");
        config.setDimension(3);
        config.setTimeoutSeconds(5);

        EmbeddingService service = new EmbeddingService(config);
        List<float[]> embeddings = service.embedBatch(List.of("alpha"));

        assertEquals(1, nativeRequests.get());
        assertEquals(1, openAiRequests.get());
        assertEquals(1, embeddings.size());
        assertArrayEquals(new float[] {7.0f, 8.0f, 9.0f}, embeddings.get(0));
    }

    @Test
    @DisplayName("should call llama.cpp native embedding endpoint when configured")
    void shouldUseLlamaCppEmbeddingEndpoint() throws Exception {
        AtomicInteger llamaRequests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/embedding", exchange -> {
            llamaRequests.incrementAndGet();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(body.contains("\"content\":\"alpha\""));
            writeJson(exchange, 200, """
                    {"embedding":[0.1,0.2,0.3]}
                    """);
        });
        server.start();

        EmbeddingConfig config = new EmbeddingConfig();
        config.setServiceUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/embedding");
        config.setApiFormat("llama_cpp");
        config.setDimension(3);
        config.setTimeoutSeconds(5);

        EmbeddingService service = new EmbeddingService(config);
        List<float[]> embeddings = service.embedBatch(List.of("alpha"));

        assertEquals(1, llamaRequests.get());
        assertEquals(1, embeddings.size());
        assertArrayEquals(new float[] {0.1f, 0.2f, 0.3f}, embeddings.get(0), 0.0001f);
    }

    @Test
    @DisplayName("should partition embedding requests using configured batch size")
    void shouldPartitionRequestsByConfiguredBatchSize() throws Exception {
        AtomicInteger openAiRequests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/embeddings", exchange -> {
            openAiRequests.incrementAndGet();
            drain(exchange.getRequestBody());
            writeJson(exchange, 200, """
                    {"data":[{"embedding":[1.0,2.0,3.0]}]}
                    """);
        });
        server.start();

        EmbeddingConfig config = new EmbeddingConfig();
        config.setModelName("test-model");
        config.setServiceUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/embeddings");
        config.setApiFormat("openai");
        config.setBatchSize(1);
        config.setDimension(3);
        config.setTimeoutSeconds(5);

        EmbeddingService service = new EmbeddingService(config);
        List<float[]> embeddings = service.embedBatch(List.of("alpha", "beta"));

        assertEquals(2, openAiRequests.get());
        assertEquals(2, embeddings.size());
    }

    private static void writeJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, payload.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(payload);
        } finally {
            exchange.close();
        }
    }

    private static void drain(InputStream inputStream) throws IOException {
        inputStream.readAllBytes();
        inputStream.close();
    }
}
