package com.walden.cvect.service.job;

import com.walden.cvect.logging.aop.AppLog;
import com.walden.cvect.logging.aop.AuditAction;
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
import com.walden.cvect.infra.vector.VectorStoreService;
import com.walden.cvect.service.matching.PersistedMatchScoreService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class JobDescriptionApplicationService {

    private final JobDescriptionJpaRepository jdRepository;
    private final CandidateJpaRepository candidateRepository;
    private final CandidateSnapshotJpaRepository snapshotRepository;
    private final ContactJpaRepository contactRepository;
    private final LinkJpaRepository linkRepository;
    private final HonorJpaRepository honorRepository;
    private final EducationJpaRepository educationRepository;
    private final ExperienceJpaRepository experienceRepository;
    private final UploadBatchJpaRepository batchRepository;
    private final UploadItemJpaRepository itemRepository;
    private final VectorStoreService vectorStoreService;
    private final PersistedMatchScoreService persistedMatchScoreService;

    public JobDescriptionApplicationService(
            JobDescriptionJpaRepository jdRepository,
            CandidateJpaRepository candidateRepository,
            CandidateSnapshotJpaRepository snapshotRepository,
            ContactJpaRepository contactRepository,
            LinkJpaRepository linkRepository,
            HonorJpaRepository honorRepository,
            EducationJpaRepository educationRepository,
            ExperienceJpaRepository experienceRepository,
            UploadBatchJpaRepository batchRepository,
            UploadItemJpaRepository itemRepository,
            VectorStoreService vectorStoreService,
            PersistedMatchScoreService persistedMatchScoreService) {
        this.jdRepository = jdRepository;
        this.candidateRepository = candidateRepository;
        this.snapshotRepository = snapshotRepository;
        this.contactRepository = contactRepository;
        this.linkRepository = linkRepository;
        this.honorRepository = honorRepository;
        this.educationRepository = educationRepository;
        this.experienceRepository = experienceRepository;
        this.batchRepository = batchRepository;
        this.itemRepository = itemRepository;
        this.vectorStoreService = vectorStoreService;
        this.persistedMatchScoreService = persistedMatchScoreService;
    }

    @AppLog(action = "create_job_description")
    @AuditAction(action = "create_job_description", target = "job_description")
    public JobDescription create(String title, String content) {
        JobDescription saved = jdRepository.save(new JobDescription(title, content));
        persistedMatchScoreService.markJobDescriptionDirty(saved.getId());
        persistedMatchScoreService.scheduleRefreshForJobDescription(saved.getId());
        return saved;
    }

    @AppLog(action = "update_job_description")
    @AuditAction(action = "update_job_description", target = "job_description", logResult = true)
    public Optional<JobDescription> update(UUID id, String title, String content) {
        return jdRepository.findById(id)
                .map(jd -> {
                    jd.setTitle(title);
                    jd.setContent(content);
                    JobDescription saved = jdRepository.save(jd);
                    persistedMatchScoreService.markJobDescriptionDirty(saved.getId());
                    persistedMatchScoreService.scheduleRefreshForJobDescription(saved.getId());
                    return saved;
                });
    }

    @AppLog(action = "delete_job_description")
    @AuditAction(action = "delete_job_description", target = "job_description", logResult = true)
    @Transactional
    public boolean delete(UUID id) {
        JobDescription jd = jdRepository.findById(id).orElse(null);
        if (jd == null) {
            return false;
        }
        List<UUID> candidateIds = candidateRepository.findIdsByJobDescriptionId(id);
        persistedMatchScoreService.deleteByJobDescriptionId(id);
        persistedMatchScoreService.deleteByCandidateIds(candidateIds);
        vectorStoreService.deleteByJobDescription(id);
        snapshotRepository.deleteByJdId(id);
        contactRepository.deleteByJobDescriptionId(id);
        linkRepository.deleteByJobDescriptionId(id);
        honorRepository.deleteByJobDescriptionId(id);
        educationRepository.deleteByJobDescriptionId(id);
        experienceRepository.deleteByJobDescriptionId(id);
        candidateRepository.deleteByJobDescriptionId(id);
        itemRepository.deleteByJobDescriptionId(id);
        batchRepository.deleteByJobDescriptionId(id);
        jdRepository.delete(jd);
        return true;
    }
}
