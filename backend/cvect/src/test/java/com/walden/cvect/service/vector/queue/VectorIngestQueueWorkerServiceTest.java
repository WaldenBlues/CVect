package com.walden.cvect.service.vector.queue;

import com.walden.cvect.infra.vector.VectorStoreService;
import com.walden.cvect.model.ChunkType;
import com.walden.cvect.model.entity.vector.VectorIngestTask;
import com.walden.cvect.model.entity.vector.VectorIngestTaskStatus;
import com.walden.cvect.repository.VectorIngestTaskJpaRepository;
import com.walden.cvect.service.candidate.CandidateSnapshotService;
import com.walden.cvect.service.matching.PersistedMatchScoreService;
import com.walden.cvect.service.vector.queue.VectorIngestQueueWorkerService;
import com.walden.cvect.web.stream.CandidateStreamService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
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
