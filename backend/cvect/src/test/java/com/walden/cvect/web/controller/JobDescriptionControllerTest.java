package com.walden.cvect.web.controller;

import com.walden.cvect.infra.vector.VectorStoreService;
import com.walden.cvect.repository.CandidateJpaRepository;
import com.walden.cvect.repository.CandidateSnapshotJpaRepository;
import com.walden.cvect.repository.ContactJpaRepository;
import com.walden.cvect.repository.EducationJpaRepository;
import com.walden.cvect.repository.ExperienceJpaRepository;
import com.walden.cvect.repository.HonorJpaRepository;
import com.walden.cvect.repository.JobDescriptionJpaRepository;
import com.walden.cvect.repository.LinkJpaRepository;
import com.walden.cvect.repository.UploadBatchJpaRepository;
import com.walden.cvect.repository.UploadItemJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

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
    private CandidateSnapshotJpaRepository snapshotRepository;
    @Mock
    private ContactJpaRepository contactRepository;
    @Mock
    private LinkJpaRepository linkRepository;
    @Mock
    private HonorJpaRepository honorRepository;
    @Mock
    private EducationJpaRepository educationRepository;
    @Mock
    private ExperienceJpaRepository experienceRepository;
    @Mock
    private UploadBatchJpaRepository batchRepository;
    @Mock
    private UploadItemJpaRepository itemRepository;
    @Mock
    private VectorStoreService vectorStoreService;

    @Test
    @DisplayName("list should return empty array when no JD exists")
    void listShouldReturnEmptyArrayWhenNoJdExists() {
        when(jdRepository.findAll()).thenReturn(List.of());

        JobDescriptionController controller = new JobDescriptionController(
                jdRepository,
                candidateRepository,
                snapshotRepository,
                contactRepository,
                linkRepository,
                honorRepository,
                educationRepository,
                experienceRepository,
                batchRepository,
                itemRepository,
                vectorStoreService);

        ResponseEntity<List<JobDescriptionController.JobDescriptionSummary>> response = controller.list();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
        verify(candidateRepository, never()).countGroupedByJobDescriptionIds(org.mockito.ArgumentMatchers.any());
    }
}
