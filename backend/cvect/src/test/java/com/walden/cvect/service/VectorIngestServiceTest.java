package com.walden.cvect.service;

import com.walden.cvect.infra.vector.VectorStoreService;
import com.walden.cvect.model.ChunkType;
import com.walden.cvect.model.entity.vector.VectorIngestTaskStatus;
import com.walden.cvect.repository.VectorIngestTaskJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("VectorIngestService unit tests")
class VectorIngestServiceTest {

    @Mock
    private VectorIngestTaskJpaRepository taskRepository;
    @Mock
    private VectorStoreService vectorStoreService;

    @Test
    @DisplayName("ingest should skip enqueue when vector is disabled")
    void shouldSkipWhenVectorDisabled() {
        VectorIngestService service = new VectorIngestService(taskRepository, vectorStoreService, false, true, 5000);

        service.ingest(UUID.randomUUID(), ChunkType.EXPERIENCE, "java backend");

        verify(taskRepository, never()).countByStatusIn(any());
        verify(taskRepository, never()).save(any());
        verify(vectorStoreService, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("ingest should skip enqueue when worker is disabled")
    void shouldSkipWhenWorkerDisabled() {
        VectorIngestService service = new VectorIngestService(taskRepository, vectorStoreService, true, false, 5000);

        service.ingest(UUID.randomUUID(), ChunkType.SKILL, "spring boot");

        verify(taskRepository, never()).countByStatusIn(any());
        verify(taskRepository, never()).save(any());
        verify(vectorStoreService, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("ingest should enqueue when vector and worker are enabled and queue has capacity")
    void shouldEnqueueWhenEnabledAndQueueHasCapacity() {
        VectorIngestService service = new VectorIngestService(taskRepository, vectorStoreService, true, true, 10);
        UUID candidateId = UUID.randomUUID();
        when(taskRepository.countByStatusIn(eq(List.of(
                VectorIngestTaskStatus.PENDING,
                VectorIngestTaskStatus.PROCESSING)))).thenReturn(3L);

        service.ingest(candidateId, ChunkType.EXPERIENCE, "distributed systems");

        ArgumentCaptor<com.walden.cvect.model.entity.vector.VectorIngestTask> captor =
                ArgumentCaptor.forClass(com.walden.cvect.model.entity.vector.VectorIngestTask.class);
        verify(taskRepository).save(captor.capture());
        assertEquals(candidateId, captor.getValue().getCandidateId());
        assertEquals(ChunkType.EXPERIENCE, captor.getValue().getChunkType());
        assertEquals("distributed systems", captor.getValue().getContent());
        verify(vectorStoreService, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("ingest should fallback to inline persistence when queue is full")
    void shouldFallbackInlineWhenQueueIsFull() {
        VectorIngestService service = new VectorIngestService(taskRepository, vectorStoreService, true, true, 2);
        UUID candidateId = UUID.randomUUID();
        when(taskRepository.countByStatusIn(eq(List.of(
                VectorIngestTaskStatus.PENDING,
                VectorIngestTaskStatus.PROCESSING)))).thenReturn(2L);
        when(vectorStoreService.save(candidateId, ChunkType.SKILL, "spring boot")).thenReturn(true);

        service.ingest(candidateId, ChunkType.SKILL, "spring boot");

        verify(taskRepository, never()).save(any());
        verify(vectorStoreService).save(candidateId, ChunkType.SKILL, "spring boot");
    }

    @Test
    @DisplayName("ingest should fallback to inline persistence when enqueue fails")
    void shouldFallbackInlineWhenEnqueueFails() {
        VectorIngestService service = new VectorIngestService(taskRepository, vectorStoreService, true, true, 10);
        UUID candidateId = UUID.randomUUID();
        when(taskRepository.countByStatusIn(eq(List.of(
                VectorIngestTaskStatus.PENDING,
                VectorIngestTaskStatus.PROCESSING)))).thenReturn(0L);
        when(taskRepository.save(any())).thenThrow(new RuntimeException("db down"));
        when(vectorStoreService.save(candidateId, ChunkType.EXPERIENCE, "java backend")).thenReturn(true);

        service.ingest(candidateId, ChunkType.EXPERIENCE, "java backend");

        verify(vectorStoreService).save(candidateId, ChunkType.EXPERIENCE, "java backend");
    }
}
