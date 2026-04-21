package com.walden.cvect.service.job;

import com.walden.cvect.infra.vector.VectorStoreService;
import com.walden.cvect.model.TenantConstants;
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
import com.walden.cvect.security.CurrentUserService;
import com.walden.cvect.security.DataScopeService;
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
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private DataScopeService dataScopeService;

    @Test
    @DisplayName("delete should cascade related data when JD exists")
    void deleteShouldCascadeRelatedData() {
        UUID jdId = UUID.randomUUID();
        UUID tenantId = TenantConstants.DEFAULT_TENANT_ID;
        JobDescription jd = new JobDescription("Backend", "Spring");
        when(currentUserService.currentTenantId()).thenReturn(tenantId);
        when(dataScopeService.hasTenantWideScope()).thenReturn(true);
        when(jdRepository.findByIdAndTenantId(jdId, tenantId)).thenReturn(Optional.of(jd));
        when(candidateRepository.findIdsByTenantIdAndJobDescriptionId(tenantId, jdId)).thenReturn(List.of());

        JobDescriptionApplicationService service = service();

        assertTrue(service.delete(jdId));
        verify(persistedMatchScoreService).deleteByJobDescriptionId(jdId);
        verify(vectorStoreService).deleteByJobDescription(jdId);
        verify(snapshotRepository).deleteByTenantIdAndJdId(tenantId, jdId);
        verify(contactRepository).deleteByJobDescriptionId(jdId);
        verify(linkRepository).deleteByJobDescriptionId(jdId);
        verify(honorRepository).deleteByJobDescriptionId(jdId);
        verify(educationRepository).deleteByJobDescriptionId(jdId);
        verify(experienceRepository).deleteByJobDescriptionId(jdId);
        verify(candidateRepository).deleteByTenantIdAndJobDescriptionId(tenantId, jdId);
        verify(itemRepository).deleteByTenantIdAndJobDescriptionId(tenantId, jdId);
        verify(batchRepository).deleteByTenantIdAndJobDescriptionId(tenantId, jdId);
        verify(jdRepository).delete(jd);
    }

    @Test
    @DisplayName("delete should return false when JD does not exist")
    void deleteShouldReturnFalseWhenJdMissing() {
        UUID jdId = UUID.randomUUID();
        UUID tenantId = TenantConstants.DEFAULT_TENANT_ID;
        when(currentUserService.currentTenantId()).thenReturn(tenantId);
        when(dataScopeService.hasTenantWideScope()).thenReturn(true);
        when(jdRepository.findByIdAndTenantId(jdId, tenantId)).thenReturn(Optional.empty());

        JobDescriptionApplicationService service = service();

        assertFalse(service.delete(jdId));
    }

    private JobDescriptionApplicationService service() {
        return new JobDescriptionApplicationService(
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
                persistedMatchScoreService,
                currentUserService,
                dataScopeService);
    }
}
