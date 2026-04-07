package com.walden.cvect.web.controller.job;

import com.walden.cvect.model.entity.JobDescription;
import com.walden.cvect.repository.CandidateJpaRepository;
import com.walden.cvect.repository.JobDescriptionJpaRepository;
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

    @Test
    @DisplayName("list should return empty array when no JD exists")
    void listShouldReturnEmptyArrayWhenNoJdExists() {
        when(jdRepository.findAll()).thenReturn(List.of());

        JobDescriptionController controller = new JobDescriptionController(
                jdRepository,
                candidateRepository,
                jobDescriptionApplicationService);

        ResponseEntity<List<JobDescriptionController.JobDescriptionSummary>> response = controller.list();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
        verify(candidateRepository, never()).countGroupedByJobDescriptionIds(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("delete should delegate to application service")
    void deleteShouldDelegateToApplicationService() {
        UUID jdId = UUID.randomUUID();
        when(jobDescriptionApplicationService.delete(jdId)).thenReturn(true);

        JobDescriptionController controller = new JobDescriptionController(
                jdRepository,
                candidateRepository,
                jobDescriptionApplicationService);

        ResponseEntity<Void> response = controller.delete(jdId);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(jobDescriptionApplicationService).delete(jdId);
    }
}
