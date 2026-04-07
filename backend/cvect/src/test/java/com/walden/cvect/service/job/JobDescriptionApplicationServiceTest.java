package com.walden.cvect.service.job;

import com.walden.cvect.infra.vector.VectorStoreService;
import com.walden.cvect.model.entity.JobDescription;
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
import com.walden.cvect.service.job.JobDescriptionApplicationService;
import com.walden.cvect.service.matching.PersistedMatchScoreService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JobDescriptionApplicationService unit tests")
class JobDescriptionApplicationServiceTest {

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
    @Mock
    private PersistedMatchScoreService persistedMatchScoreService;

    @Test
    @DisplayName("delete should cascade related data when JD exists")
    void deleteShouldCascadeRelatedData() {
        UUID jdId = UUID.randomUUID();
        JobDescription jd = new JobDescription("Backend", "Spring");
        when(jdRepository.findById(jdId)).thenReturn(Optional.of(jd));
        when(candidateRepository.findIdsByJobDescriptionId(jdId)).thenReturn(List.of());

        JobDescriptionApplicationService service = new JobDescriptionApplicationService(
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
                vectorStoreService,
                persistedMatchScoreService);

        assertTrue(service.delete(jdId));
        verify(persistedMatchScoreService).deleteByJobDescriptionId(jdId);
        verify(vectorStoreService).deleteByJobDescription(jdId);
        verify(snapshotRepository).deleteByJdId(jdId);
        verify(contactRepository).deleteByJobDescriptionId(jdId);
        verify(linkRepository).deleteByJobDescriptionId(jdId);
        verify(honorRepository).deleteByJobDescriptionId(jdId);
        verify(educationRepository).deleteByJobDescriptionId(jdId);
        verify(experienceRepository).deleteByJobDescriptionId(jdId);
        verify(candidateRepository).deleteByJobDescriptionId(jdId);
        verify(itemRepository).deleteByJobDescriptionId(jdId);
        verify(batchRepository).deleteByJobDescriptionId(jdId);
        verify(jdRepository).delete(jd);
    }

    @Test
    @DisplayName("delete should return false when JD does not exist")
    void deleteShouldReturnFalseWhenJdMissing() {
        UUID jdId = UUID.randomUUID();
        when(jdRepository.findById(jdId)).thenReturn(Optional.empty());

        JobDescriptionApplicationService service = new JobDescriptionApplicationService(
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
                vectorStoreService,
                persistedMatchScoreService);

        assertFalse(service.delete(jdId));
    }
}
