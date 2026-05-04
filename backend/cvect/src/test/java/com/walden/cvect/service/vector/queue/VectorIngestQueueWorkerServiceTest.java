package com.walden.cvect.service.vector.queue;

import com.walden.cvect.infra.vector.VectorStoreService;
import com.walden.cvect.model.ChunkType;
import com.walden.cvect.model.entity.vector.VectorIngestTask;
import com.walden.cvect.model.entity.vector.VectorIngestTaskStatus;
import com.walden.cvect.repository.VectorIngestTaskJpaRepository;
import com.walden.cvect.service.candidate.CandidateSnapshotService;
import com.walden.cvect.service.matching.PersistedMatchScoreService;
import com.walden.cvect.service.vector.queue.VectorIngestQueueWorkerService;
import com.walden.cvect.web.stream.CandidateStreamEvent;
import com.walden.cvect.web.stream.CandidateStreamService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("VectorIngestQueueWorkerService unit tests")
class VectorIngestQueueWorkerServiceTest {

    @Mock
    private VectorIngestTaskJpaRepository taskRepository;
    @Mock
    private VectorStoreService vectorStoreService;
    @Mock
    private CandidateSnapshotService snapshotService;
    @Mock
    private CandidateStreamService streamService;
    @Mock
    private PersistedMatchScoreService persistedMatchScoreService;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("consumePendingTasks should mark vector ingest task done and publish status when processing succeeds")
    void shouldMarkTaskDoneAndPublishStatusWhenProcessingSucceeds() {
        stubNoOpTransactions();
        UUID candidateId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID jdId = UUID.randomUUID();
        VectorIngestTask processingTask = processingTask(candidateId);
        UUID taskId = processingTask.getId();

        when(taskRepository.findTop50ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(any(), any()))
                .thenReturn(List.of());
        when(taskRepository.findByStatusOrderByUpdatedAtAsc(any(), any(Pageable.class)))
                .thenReturn(List.of(processingTask));
        when(taskRepository.claimPendingTaskById(any(UUID.class), any(), any())).thenReturn(1);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(processingTask));
        when(vectorStoreService.save(candidateId, ChunkType.SKILL, "content")).thenReturn(true);
        when(taskRepository.completeSuccess(taskId, VectorIngestTaskStatus.DONE, VectorIngestTaskStatus.PROCESSING))
                .thenReturn(1);
        when(taskRepository.existsByCandidateIdAndStatusIn(
                candidateId,
                List.of(VectorIngestTaskStatus.PENDING, VectorIngestTaskStatus.PROCESSING)))
                .thenReturn(false);
        when(taskRepository.existsByCandidateIdAndStatus(candidateId, VectorIngestTaskStatus.DONE))
                .thenReturn(true);
        when(snapshotService.build(candidateId, "VECTOR_DONE")).thenReturn(new CandidateStreamEvent(
                candidateId,
                tenantId,
                jdId,
                "VECTOR_DONE",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()));

        VectorIngestQueueWorkerService service = new VectorIngestQueueWorkerService(
                taskRepository,
                vectorStoreService,
                snapshotService,
                streamService,
                persistedMatchScoreService,
                jdbcTemplate,
                transactionManager,
                20,
                3,
                300_000,
                5_000);
        service.consumePendingTasks();

        verify(taskRepository).completeSuccess(taskId, VectorIngestTaskStatus.DONE, VectorIngestTaskStatus.PROCESSING);
        verify(persistedMatchScoreService).refreshForCandidate(candidateId);

        ArgumentCaptor<com.walden.cvect.web.stream.VectorStatusStreamEvent> eventCaptor =
                ArgumentCaptor.forClass(com.walden.cvect.web.stream.VectorStatusStreamEvent.class);
        verify(streamService).publishVectorStatus(eventCaptor.capture());
        assertEquals(candidateId, eventCaptor.getValue().candidateId());
        assertEquals("VECTOR_DONE", eventCaptor.getValue().status());
    }

    @Test
    @DisplayName("consumePendingTasks should keep vector ingest task retryable and record failure reason before max attempts")
    void shouldKeepTaskRetryableBeforeMaxAttempts() {
        stubNoOpTransactions();
        UUID candidateId = UUID.randomUUID();
        VectorIngestTask processingTask = processingTask(candidateId);
        UUID taskId = processingTask.getId();
        processingTask.setAttempt(0);

        when(taskRepository.findTop50ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(any(), any()))
                .thenReturn(List.of());
        when(taskRepository.findByStatusOrderByUpdatedAtAsc(any(), any(Pageable.class)))
                .thenReturn(List.of(processingTask));
        when(taskRepository.claimPendingTaskById(any(UUID.class), any(), any())).thenReturn(1);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(processingTask));
        when(vectorStoreService.save(candidateId, ChunkType.SKILL, "content"))
                .thenThrow(new RuntimeException("connection refused"));
        when(taskRepository.completeFailure(
                taskId,
                1,
                "connection refused",
                VectorIngestTaskStatus.PENDING,
                VectorIngestTaskStatus.PROCESSING)).thenReturn(1);

        VectorIngestQueueWorkerService service = new VectorIngestQueueWorkerService(
                taskRepository,
                vectorStoreService,
                snapshotService,
                streamService,
                persistedMatchScoreService,
                jdbcTemplate,
                transactionManager,
                20,
                3,
                300_000,
                5_000);
        service.consumePendingTasks();

        verify(taskRepository).completeFailure(
                taskId,
                1,
                "connection refused",
                VectorIngestTaskStatus.PENDING,
                VectorIngestTaskStatus.PROCESSING);
        verify(streamService, never()).publishVectorStatus(any());
        verify(persistedMatchScoreService, never()).refreshForCandidate(any());
    }

    @Test
    @DisplayName("consumePendingTasks should mark vector ingest task failed and record failure reason after max attempts")
    void shouldMarkTaskFailedAfterMaxAttempts() {
        stubNoOpTransactions();
        UUID candidateId = UUID.randomUUID();
        VectorIngestTask processingTask = processingTask(candidateId);
        UUID taskId = processingTask.getId();
        processingTask.setAttempt(2);

        when(taskRepository.findTop50ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(any(), any()))
                .thenReturn(List.of());
        when(taskRepository.findByStatusOrderByUpdatedAtAsc(any(), any(Pageable.class)))
                .thenReturn(List.of(processingTask));
        when(taskRepository.claimPendingTaskById(any(UUID.class), any(), any())).thenReturn(1);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(processingTask));
        when(vectorStoreService.save(candidateId, ChunkType.SKILL, "content"))
                .thenThrow(new RuntimeException("bad chunk"));
        when(taskRepository.completeFailure(
                taskId,
                3,
                "bad chunk",
                VectorIngestTaskStatus.FAILED,
                VectorIngestTaskStatus.PROCESSING)).thenReturn(1);

        VectorIngestQueueWorkerService service = new VectorIngestQueueWorkerService(
                taskRepository,
                vectorStoreService,
                snapshotService,
                streamService,
                persistedMatchScoreService,
                jdbcTemplate,
                transactionManager,
                20,
                3,
                300_000,
                5_000);
        service.consumePendingTasks();

        verify(taskRepository).completeFailure(
                taskId,
                3,
                "bad chunk",
                VectorIngestTaskStatus.FAILED,
                VectorIngestTaskStatus.PROCESSING);
        verify(streamService, never()).publishVectorStatus(any());
    }

    @Test
    @DisplayName("consumePendingTasks should skip publishing when success status transition is stale")
    void shouldSkipPublishWhenSuccessCommitIsStale() {
        stubNoOpTransactions();
        UUID candidateId = UUID.randomUUID();
        VectorIngestTask processingTask = processingTask(candidateId);
        UUID taskId = processingTask.getId();

        when(taskRepository.findTop50ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(any(), any()))
                .thenReturn(List.of());
        when(taskRepository.findByStatusOrderByUpdatedAtAsc(any(), any(Pageable.class)))
                .thenReturn(List.of(processingTask));
        when(taskRepository.claimPendingTaskById(any(UUID.class), any(), any())).thenReturn(1);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(processingTask));
        when(vectorStoreService.save(candidateId, ChunkType.SKILL, "content")).thenReturn(true);
        when(taskRepository.completeSuccess(taskId, VectorIngestTaskStatus.DONE, VectorIngestTaskStatus.PROCESSING))
                .thenReturn(0);

        VectorIngestQueueWorkerService service = new VectorIngestQueueWorkerService(
                taskRepository,
                vectorStoreService,
                snapshotService,
                streamService,
                persistedMatchScoreService,
                jdbcTemplate,
                transactionManager,
                20,
                3,
                300_000,
                5_000);
        service.consumePendingTasks();

        verify(taskRepository, never()).existsByCandidateIdAndStatusIn(any(UUID.class), anyCollection());
        verify(streamService, never()).publishVectorStatus(any());
    }

    @Test
    @DisplayName("consumePendingTasks should skip stale failure commit side effects")
    void shouldSkipFailureSideEffectsWhenFailureCommitIsStale() {
        stubNoOpTransactions();
        UUID candidateId = UUID.randomUUID();
        VectorIngestTask processingTask = processingTask(candidateId);
        UUID taskId = processingTask.getId();

        when(taskRepository.findTop50ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(any(), any()))
                .thenReturn(List.of());
        when(taskRepository.findByStatusOrderByUpdatedAtAsc(any(), any(Pageable.class)))
                .thenReturn(List.of(processingTask));
        when(taskRepository.claimPendingTaskById(any(UUID.class), any(), any())).thenReturn(1);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(processingTask));
        when(vectorStoreService.save(candidateId, ChunkType.SKILL, "content")).thenThrow(new RuntimeException("boom"));
        when(taskRepository.completeFailure(
                any(UUID.class),
                anyInt(),
                any(String.class),
                any(),
                any())).thenReturn(0);

        VectorIngestQueueWorkerService service = new VectorIngestQueueWorkerService(
                taskRepository,
                vectorStoreService,
                snapshotService,
                streamService,
                persistedMatchScoreService,
                jdbcTemplate,
                transactionManager,
                20,
                3,
                300_000,
                5_000);
        service.consumePendingTasks();

        verify(streamService, never()).publishVectorStatus(any());
    }

    private void stubNoOpTransactions() {
        TransactionStatus txStatus = new SimpleTransactionStatus();
        when(transactionManager.getTransaction(any())).thenReturn(txStatus);
    }

    private static VectorIngestTask processingTask(UUID candidateId) {
        VectorIngestTask task = new VectorIngestTask(candidateId, ChunkType.SKILL, "content");
        task.setStatus(VectorIngestTaskStatus.PROCESSING);
        return task;
    }
}
