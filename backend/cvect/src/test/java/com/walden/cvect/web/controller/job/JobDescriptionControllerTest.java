package com.walden.cvect.web.controller.job;

import com.walden.cvect.model.TenantConstants;
import com.walden.cvect.model.entity.JobDescription;
import com.walden.cvect.repository.CandidateJpaRepository;
import com.walden.cvect.repository.JobDescriptionJpaRepository;
import com.walden.cvect.security.CurrentUserService;
import com.walden.cvect.security.DataScopeService;
import com.walden.cvect.service.job.JobDescriptionApplicationService;
import com.walden.cvect.web.controller.job.JobDescriptionController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JobDescriptionController unit tests")
class JobDescriptionControllerTest {

    @Mock
    private JobDescriptionJpaRepository jdRepository;
    @Mock
    private CandidateJpaRepository candidateRepository;
    @Mock
    private JobDescriptionApplicationService jobDescriptionApplicationService;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private DataScopeService dataScopeService;

    @Test
    @DisplayName("list should return empty array when no JD exists")
    void listShouldReturnEmptyArrayWhenNoJdExists() {
        when(currentUserService.currentTenantId()).thenReturn(TenantConstants.DEFAULT_TENANT_ID);
        when(dataScopeService.hasTenantWideScope()).thenReturn(true);
        when(jdRepository.findByTenantIdOrderByCreatedAtDesc(TenantConstants.DEFAULT_TENANT_ID)).thenReturn(List.of());

        JobDescriptionController controller = new JobDescriptionController(
                jdRepository,
                candidateRepository,
                jobDescriptionApplicationService,
                currentUserService,
                dataScopeService);

        ResponseEntity<List<JobDescriptionController.JobDescriptionSummary>> response = controller.list();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
        verify(candidateRepository, never()).countGroupedByTenantIdAndJobDescriptionIds(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("list should use creator scope for recruiter view")
    void listShouldUseCreatorScopeForRecruiterView() {
        UUID userId = UUID.randomUUID();
        when(currentUserService.currentTenantId()).thenReturn(TenantConstants.DEFAULT_TENANT_ID);
        when(dataScopeService.hasTenantWideScope()).thenReturn(false);
        when(dataScopeService.currentUserIdOrNull()).thenReturn(userId);
        when(jdRepository.findByTenantIdAndCreatedByUserIdOrderByCreatedAtDesc(
                TenantConstants.DEFAULT_TENANT_ID,
                userId)).thenReturn(List.of());

        JobDescriptionController controller = new JobDescriptionController(
                jdRepository,
                candidateRepository,
                jobDescriptionApplicationService,
                currentUserService,
                dataScopeService);

        ResponseEntity<List<JobDescriptionController.JobDescriptionSummary>> response = controller.list();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
        verify(jdRepository, never()).findByTenantIdOrderByCreatedAtDesc(TenantConstants.DEFAULT_TENANT_ID);
    }

    @Test
    @DisplayName("delete should delegate to application service")
    void deleteShouldDelegateToApplicationService() {
        UUID jdId = UUID.randomUUID();
        when(jobDescriptionApplicationService.delete(jdId)).thenReturn(true);

        JobDescriptionController controller = new JobDescriptionController(
                jdRepository,
                candidateRepository,
                jobDescriptionApplicationService,
                currentUserService,
                dataScopeService);

        ResponseEntity<Void> response = controller.delete(jdId);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(jobDescriptionApplicationService).delete(jdId);
    }
}
