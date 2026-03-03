package com.walden.cvect.web.controller;

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
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JD 组管理 API
 */
@RestController
@RequestMapping("/api/jds")
public class JobDescriptionController {

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

    public JobDescriptionController(JobDescriptionJpaRepository jdRepository,
            CandidateJpaRepository candidateRepository,
            CandidateSnapshotJpaRepository snapshotRepository,
            ContactJpaRepository contactRepository,
            LinkJpaRepository linkRepository,
            HonorJpaRepository honorRepository,
            EducationJpaRepository educationRepository,
            ExperienceJpaRepository experienceRepository,
            UploadBatchJpaRepository batchRepository,
            UploadItemJpaRepository itemRepository,
            VectorStoreService vectorStoreService) {
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
    }

    @GetMapping
    public ResponseEntity<List<JobDescriptionSummary>> list() {
        List<JobDescription> jds = jdRepository.findAll();
        if (jds.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        Map<UUID, Long> countsByJdId = candidateRepository.countGroupedByJobDescriptionIds(
                        jds.stream().map(JobDescription::getId).toList())
                .stream()
                .collect(Collectors.toMap(
                        CandidateJpaRepository.JobDescriptionCandidateCount::getJdId,
                        CandidateJpaRepository.JobDescriptionCandidateCount::getCount,
                        (a, b) -> a));
        List<JobDescriptionSummary> data = jds.stream()
                .map(jd -> toSummary(jd, countsByJdId.getOrDefault(jd.getId(), 0L)))
                .toList();
        return ResponseEntity.ok(data);
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobDescriptionSummary> detail(@PathVariable UUID id) {
        return jdRepository.findById(id)
                .map(jd -> ResponseEntity.ok(toSummary(jd)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<JobDescriptionSummary> create(@Valid @RequestBody JobDescriptionRequest request) {
        JobDescription saved = jdRepository.save(new JobDescription(request.title(), request.content()));
        return ResponseEntity.ok(toSummary(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<JobDescriptionSummary> update(
            @PathVariable UUID id,
            @Valid @RequestBody JobDescriptionRequest request) {
        return jdRepository.findById(id)
                .map(jd -> {
                    jd.setTitle(request.title());
                    jd.setContent(request.content());
                    JobDescription saved = jdRepository.save(jd);
                    return ResponseEntity.ok(toSummary(saved));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        JobDescription jd = jdRepository.findById(id).orElse(null);
        if (jd == null) {
            return ResponseEntity.notFound().build();
        }
        if (candidateRepository.countByJobDescriptionId(id) > 0) {
            return ResponseEntity.status(409).build();
        }
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
        return ResponseEntity.noContent().build();
    }

    private JobDescriptionSummary toSummary(JobDescription jd) {
        return toSummary(jd, candidateRepository.countByJobDescriptionId(jd.getId()));
    }

    private JobDescriptionSummary toSummary(JobDescription jd, long candidateCount) {
        return new JobDescriptionSummary(
                jd.getId(),
                jd.getTitle(),
                jd.getContent(),
                jd.getCreatedAt(),
                candidateCount);
    }

    public record JobDescriptionRequest(
            @NotBlank String title,
            String content
    ) {}

    public record JobDescriptionSummary(
            UUID id,
            String title,
            String content,
            java.time.LocalDateTime createdAt,
            long candidateCount
    ) {}
}
