package com.walden.cvect.web.controller.candidate;

import com.walden.cvect.model.entity.Candidate;
import com.walden.cvect.model.entity.CandidateRecruitmentStatus;
import com.walden.cvect.model.entity.JobDescription;
import com.walden.cvect.model.TenantConstants;
import com.walden.cvect.repository.CandidateMatchScoreJpaRepository;
import com.walden.cvect.repository.CandidateJpaRepository;
import com.walden.cvect.repository.ResumeChunkVectorJpaRepository;
import com.walden.cvect.repository.VectorIngestTaskJpaRepository;
import com.walden.cvect.security.CurrentUserService;
import com.walden.cvect.security.DataScopeService;
import com.walden.cvect.service.candidate.CandidateSnapshotService;
import com.walden.cvect.service.matching.PersistedMatchScoreService;
import com.walden.cvect.web.controller.candidate.CandidateController;
import com.walden.cvect.web.stream.CandidateStreamEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("CandidateController unit tests")
class CandidateControllerTest {

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
    @DisplayName("listByJd should not query vector ids when candidate list is empty")
    void listByJdShouldSkipVectorQueryWhenNoCandidates() {
        UUID jdId = UUID.randomUUID();
        when(currentUserService.currentTenantId()).thenReturn(TenantConstants.DEFAULT_TENANT_ID);
        when(dataScopeService.hasTenantWideScope()).thenReturn(true);
        when(candidateRepository.findByTenantIdAndJobDescriptionIdOrderByCreatedAtDesc(
                TenantConstants.DEFAULT_TENANT_ID,
                jdId)).thenReturn(List.<Candidate>of());
        when(snapshotService.listByTenantAndJd(TenantConstants.DEFAULT_TENANT_ID, jdId))
                .thenReturn(List.<CandidateStreamEvent>of());

        CandidateController controller = controller();

        ResponseEntity<List<CandidateController.CandidateListItem>> response = controller.listByJd(jdId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().size());
        verify(resumeChunkVectorRepository, never()).findDistinctCandidateIdsIn(anyCollection());
    }

    @Test
    @DisplayName("updateRecruitmentStatus should return fallback event when snapshot build fails")
    void updateRecruitmentStatusShouldReturnFallbackEventWhenSnapshotBuildFails() {
        UUID candidateId = UUID.randomUUID();
        UUID jdId = UUID.randomUUID();

        Candidate candidate = mock(Candidate.class);
        JobDescription jd = mock(JobDescription.class);
        when(jd.getId()).thenReturn(jdId);
        when(candidate.getId()).thenReturn(candidateId);
        when(candidate.getTenantId()).thenReturn(TenantConstants.DEFAULT_TENANT_ID);
        when(candidate.getJobDescription()).thenReturn(jd);
        when(candidate.getRecruitmentStatus()).thenReturn(CandidateRecruitmentStatus.TO_INTERVIEW);
        when(candidate.getName()).thenReturn("Alice");
        when(candidate.getSourceFileName()).thenReturn("alice.pdf");
        when(candidate.getContentType()).thenReturn("application/pdf");
        when(candidate.getFileSizeBytes()).thenReturn(123L);
        when(candidate.getParsedCharCount()).thenReturn(42);
        when(candidate.getTruncated()).thenReturn(false);

        when(currentUserService.currentTenantId()).thenReturn(TenantConstants.DEFAULT_TENANT_ID);
        when(dataScopeService.hasTenantWideScope()).thenReturn(true);
        when(candidateRepository.findByIdAndTenantId(candidateId, TenantConstants.DEFAULT_TENANT_ID))
                .thenReturn(Optional.of(candidate));
        when(candidateRepository.save(any(Candidate.class))).thenReturn(candidate);
        when(snapshotService.build(candidateId, "UPDATED")).thenReturn(null);

        CandidateController controller = controller();

        ResponseEntity<CandidateStreamEvent> response = controller.updateRecruitmentStatus(
                candidateId,
                new CandidateController.UpdateRecruitmentStatusRequest(CandidateRecruitmentStatus.TO_INTERVIEW));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UPDATED", response.getBody().status());
        assertEquals("TO_INTERVIEW", response.getBody().recruitmentStatus());
        assertEquals(candidateId, response.getBody().candidateId());
    }

    @Test
    @DisplayName("updateRecruitmentStatus should return fallback event when snapshot build throws")
    void updateRecruitmentStatusShouldReturnFallbackEventWhenSnapshotBuildThrows() {
        UUID candidateId = UUID.randomUUID();

        Candidate candidate = mock(Candidate.class);
        when(candidate.getId()).thenReturn(candidateId);
        when(candidate.getTenantId()).thenReturn(TenantConstants.DEFAULT_TENANT_ID);
        when(candidate.getRecruitmentStatus()).thenReturn(CandidateRecruitmentStatus.REJECTED);
        when(candidate.getName()).thenReturn("Eve");
        when(candidate.getSourceFileName()).thenReturn("eve.pdf");
        when(candidate.getContentType()).thenReturn("application/pdf");
        when(candidate.getFileSizeBytes()).thenReturn(12L);
        when(candidate.getParsedCharCount()).thenReturn(7);
        when(candidate.getTruncated()).thenReturn(false);

        when(currentUserService.currentTenantId()).thenReturn(TenantConstants.DEFAULT_TENANT_ID);
        when(dataScopeService.hasTenantWideScope()).thenReturn(true);
        when(candidateRepository.findByIdAndTenantId(candidateId, TenantConstants.DEFAULT_TENANT_ID))
                .thenReturn(Optional.of(candidate));
        when(candidateRepository.save(any(Candidate.class))).thenReturn(candidate);
        when(snapshotService.build(candidateId, "UPDATED")).thenThrow(new RuntimeException("boom"));

        CandidateController controller = controller();

        ResponseEntity<CandidateStreamEvent> response = controller.updateRecruitmentStatus(
                candidateId,
                new CandidateController.UpdateRecruitmentStatusRequest(CandidateRecruitmentStatus.REJECTED));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UPDATED", response.getBody().status());
        assertEquals("REJECTED", response.getBody().recruitmentStatus());
        assertEquals(candidateId, response.getBody().candidateId());
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
