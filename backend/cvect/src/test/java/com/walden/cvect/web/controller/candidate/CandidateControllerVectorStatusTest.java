package com.walden.cvect.web.controller.candidate;

import com.walden.cvect.model.TenantConstants;
import com.walden.cvect.model.entity.Candidate;
import com.walden.cvect.model.entity.CandidateMatchScore;
import com.walden.cvect.model.entity.CandidateRecruitmentStatus;
import com.walden.cvect.model.entity.vector.VectorIngestTaskStatus;
import com.walden.cvect.repository.CandidateMatchScoreJpaRepository;
import com.walden.cvect.repository.CandidateJpaRepository;
import com.walden.cvect.repository.ResumeChunkVectorJpaRepository;
import com.walden.cvect.repository.VectorIngestTaskJpaRepository;
import com.walden.cvect.security.CurrentUserService;
import com.walden.cvect.security.DataScopeService;
import com.walden.cvect.service.candidate.CandidateSnapshotService;
import com.walden.cvect.service.matching.PersistedMatchScoreService;
import com.walden.cvect.web.stream.CandidateStreamEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CandidateController vector status unit tests")
class CandidateControllerVectorStatusTest {

    @Mock
    private CandidateJpaRepository candidateRepository;
    @Mock
    private CandidateMatchScoreJpaRepository candidateMatchScoreRepository;
    @Mock
    private CandidateSnapshotService snapshotService;
    @Mock
    private ResumeChunkVectorJpaRepository resumeChunkVectorRepository;
    @Mock
    private VectorIngestTaskJpaRepository vectorIngestTaskRepository;
    @Mock
    private PersistedMatchScoreService persistedMatchScoreService;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private DataScopeService dataScopeService;

    @Test
    @DisplayName("listByJd should report partial vector status when vector chunk exists but ingest failed")
    void listByJdShouldReportPartialVectorStatusWhenVectorChunkExistsButIngestFailed() {
        UUID jdId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();

        Candidate candidate = mock(Candidate.class);
        when(candidate.getId()).thenReturn(candidateId);

        when(currentUserService.currentTenantId()).thenReturn(TenantConstants.DEFAULT_TENANT_ID);
        when(dataScopeService.hasTenantWideScope()).thenReturn(true);
        when(candidateRepository.findByTenantIdAndJobDescriptionIdOrderByCreatedAtDesc(
                TenantConstants.DEFAULT_TENANT_ID,
                jdId)).thenReturn(List.of(candidate));
        when(snapshotService.listByTenantAndJd(TenantConstants.DEFAULT_TENANT_ID, jdId))
                .thenReturn(List.of(new CandidateStreamEvent(
                        candidateId,
                        TenantConstants.DEFAULT_TENANT_ID,
                        jdId,
                        "DONE",
                        CandidateRecruitmentStatus.TO_CONTACT.name(),
                        "Alice",
                        "alice.pdf",
                        "application/pdf",
                        123L,
                        42,
                        false,
                        LocalDateTime.of(2024, 1, 1, 12, 0),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of())));
        when(candidateMatchScoreRepository.findByTenantIdAndJobDescriptionIdAndCandidateIdIn(
                any(),
                any(),
                anyCollection())).thenReturn(List.of(new CandidateMatchScore(
                TenantConstants.DEFAULT_TENANT_ID,
                candidateId,
                jdId,
                0.75f,
                0.7f,
                0.8f,
                LocalDateTime.of(2024, 1, 3, 12, 0))));
        when(resumeChunkVectorRepository.findDistinctCandidateIdsIn(anyCollection()))
                .thenReturn(List.of(candidateId));
        when(vectorIngestTaskRepository.findCandidateIdsByStatusIn(
                anyCollection(),
                eq(List.of(VectorIngestTaskStatus.PENDING, VectorIngestTaskStatus.PROCESSING))))
                .thenReturn(List.of());
        when(vectorIngestTaskRepository.findCandidateIdsByStatusIn(
                anyCollection(),
                eq(List.of(VectorIngestTaskStatus.FAILED))))
                .thenReturn(List.of(candidateId));

        CandidateController controller = controller();

        ResponseEntity<List<CandidateController.CandidateListItem>> response = controller.listByJd(jdId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals(candidateId, response.getBody().get(0).candidateId());
        assertEquals("PARTIAL", response.getBody().get(0).vectorStatus());
        assertFalse(response.getBody().get(0).noVectorChunk());
        verify(persistedMatchScoreService, never()).scheduleRefreshForJobDescription(jdId);
    }

    @Test
    @DisplayName("listByJd should report failed vector status when ingest failed without a vector chunk")
    void listByJdShouldReportFailedVectorStatusWhenIngestFailedWithoutVectorChunk() {
        UUID jdId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();

        Candidate candidate = mock(Candidate.class);
        when(candidate.getId()).thenReturn(candidateId);

        when(currentUserService.currentTenantId()).thenReturn(TenantConstants.DEFAULT_TENANT_ID);
        when(dataScopeService.hasTenantWideScope()).thenReturn(true);
        when(candidateRepository.findByTenantIdAndJobDescriptionIdOrderByCreatedAtDesc(
                TenantConstants.DEFAULT_TENANT_ID,
                jdId)).thenReturn(List.of(candidate));
        when(snapshotService.listByTenantAndJd(TenantConstants.DEFAULT_TENANT_ID, jdId))
                .thenReturn(List.of(new CandidateStreamEvent(
                        candidateId,
                        TenantConstants.DEFAULT_TENANT_ID,
                        jdId,
                        "DONE",
                        CandidateRecruitmentStatus.TO_CONTACT.name(),
                        "Alice",
                        "alice.pdf",
                        "application/pdf",
                        123L,
                        42,
                        false,
                        LocalDateTime.of(2024, 1, 1, 12, 0),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of())));
        when(candidateMatchScoreRepository.findByTenantIdAndJobDescriptionIdAndCandidateIdIn(
                any(),
                any(),
                anyCollection())).thenReturn(List.of());
        when(resumeChunkVectorRepository.findDistinctCandidateIdsIn(anyCollection()))
                .thenReturn(List.of());
        when(vectorIngestTaskRepository.findCandidateIdsByStatusIn(
                anyCollection(),
                eq(List.of(VectorIngestTaskStatus.PENDING, VectorIngestTaskStatus.PROCESSING))))
                .thenReturn(List.of());
        when(vectorIngestTaskRepository.findCandidateIdsByStatusIn(
                anyCollection(),
                eq(List.of(VectorIngestTaskStatus.FAILED))))
                .thenReturn(List.of(candidateId));

        CandidateController controller = controller();

        ResponseEntity<List<CandidateController.CandidateListItem>> response = controller.listByJd(jdId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals(candidateId, response.getBody().get(0).candidateId());
        assertEquals("FAILED", response.getBody().get(0).vectorStatus());
        assertTrue(response.getBody().get(0).noVectorChunk());
        verify(persistedMatchScoreService, never()).scheduleRefreshForJobDescription(jdId);
    }

    private CandidateController controller() {
        return new CandidateController(
                candidateRepository,
                candidateMatchScoreRepository,
                snapshotService,
                resumeChunkVectorRepository,
                vectorIngestTaskRepository,
                persistedMatchScoreService,
                currentUserService,
                dataScopeService);
    }
}
