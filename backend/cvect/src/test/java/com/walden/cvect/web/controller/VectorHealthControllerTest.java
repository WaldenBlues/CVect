package com.walden.cvect.web.controller;

import com.walden.cvect.infra.embedding.EmbeddingConfig;
import com.walden.cvect.infra.vector.VectorStoreService;
import com.walden.cvect.model.entity.vector.VectorIngestTaskStatus;
import com.walden.cvect.repository.VectorIngestTaskJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("VectorHealthController unit tests")
class VectorHealthControllerTest {

    @Mock
    private VectorIngestTaskJpaRepository taskRepository;
    @Mock
    private VectorStoreService vectorStoreService;

    @Test
    @DisplayName("health should return DISABLED without probing embedding when vector is disabled")
    void shouldReturnDisabledWhenVectorDisabled() {
        EmbeddingConfig config = new EmbeddingConfig();
        config.setServiceUrl("http://127.0.0.1:65535/embed");
        when(taskRepository.countByStatusIn(anyCollection())).thenReturn(0L);
        VectorHealthController controller = new VectorHealthController(taskRepository, config, vectorStoreService, false, true);

        ResponseEntity<VectorHealthController.VectorHealthResponse> response = controller.health();

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("DISABLED", response.getBody().status());
        assertEquals(false, response.getBody().embeddingReachable());
        assertEquals(null, response.getBody().embeddingHealthUrl());
    }

    @Test
    @DisplayName("health should return DEGRADED without probing embedding when worker is disabled")
    void shouldReturnDegradedWhenWorkerDisabled() {
        EmbeddingConfig config = new EmbeddingConfig();
        config.setServiceUrl("http://127.0.0.1:65535/embed");
        when(taskRepository.countByStatusIn(anyCollection())).thenAnswer(invocation -> {
            Collection<?> statuses = invocation.getArgument(0);
            if (statuses.contains(VectorIngestTaskStatus.PENDING)) {
                return 1L;
            }
            return 0L;
        });
        when(vectorStoreService.isOperational()).thenReturn(true);
        when(vectorStoreService.getAvailabilityMessage()).thenReturn(null);
        VectorHealthController controller = new VectorHealthController(taskRepository, config, vectorStoreService, true, false);

        ResponseEntity<VectorHealthController.VectorHealthResponse> response = controller.health();

        assertEquals(503, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("DEGRADED", response.getBody().status());
        assertEquals(false, response.getBody().embeddingReachable());
        assertEquals(1L, response.getBody().pendingCount());
    }

    @Test
    @DisplayName("health should resolve llama.cpp health endpoint from embedding URL")
    void shouldResolveLlamaCppHealthEndpoint() {
        EmbeddingConfig config = new EmbeddingConfig();
        config.setServiceUrl("http://127.0.0.1:65535/embedding");
        when(taskRepository.countByStatusIn(anyCollection())).thenReturn(0L);
        when(vectorStoreService.isOperational()).thenReturn(true);
        when(vectorStoreService.getAvailabilityMessage()).thenReturn(null);
        VectorHealthController controller = new VectorHealthController(taskRepository, config, vectorStoreService, true, true);

        ResponseEntity<VectorHealthController.VectorHealthResponse> response = controller.health();

        assertEquals(503, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("http://127.0.0.1:65535/health", response.getBody().embeddingHealthUrl());
    }

    @Test
    @DisplayName("health should resolve native embed endpoint to non-loading health endpoint")
    void shouldResolveNativeEmbedEndpointToHealth() {
        EmbeddingConfig config = new EmbeddingConfig();
        config.setServiceUrl("http://127.0.0.1:65535/embed");
        when(taskRepository.countByStatusIn(anyCollection())).thenReturn(0L);
        when(vectorStoreService.isOperational()).thenReturn(true);
        when(vectorStoreService.getAvailabilityMessage()).thenReturn(null);
        VectorHealthController controller = new VectorHealthController(taskRepository, config, vectorStoreService, true, true);

        ResponseEntity<VectorHealthController.VectorHealthResponse> response = controller.health();

        assertEquals(503, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("http://127.0.0.1:65535/health", response.getBody().embeddingHealthUrl());
    }
}
