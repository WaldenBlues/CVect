package com.walden.cvect.web.controller.job;

import com.walden.cvect.model.entity.JobDescription;
import com.walden.cvect.repository.CandidateJpaRepository;
import com.walden.cvect.repository.JobDescriptionJpaRepository;
import com.walden.cvect.security.CurrentUserService;
import com.walden.cvect.security.PermissionCodes;
import com.walden.cvect.service.job.JobDescriptionApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final JobDescriptionApplicationService jobDescriptionApplicationService;
    private final CurrentUserService currentUserService;

    public JobDescriptionController(JobDescriptionJpaRepository jdRepository,
            CandidateJpaRepository candidateRepository,
            JobDescriptionApplicationService jobDescriptionApplicationService,
            CurrentUserService currentUserService) {
        this.jdRepository = jdRepository;
        this.candidateRepository = candidateRepository;
        this.jobDescriptionApplicationService = jobDescriptionApplicationService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    @PreAuthorize("@permissionGuard.has(T(com.walden.cvect.security.PermissionCodes).JD_READ)")
    public ResponseEntity<List<JobDescriptionSummary>> list() {
        UUID tenantId = currentUserService.currentTenantId();
        List<JobDescription> jds = jdRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        if (jds.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        Map<UUID, Long> countsByJdId = candidateRepository.countGroupedByTenantIdAndJobDescriptionIds(
                        tenantId,
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
    @PreAuthorize("@permissionGuard.has(T(com.walden.cvect.security.PermissionCodes).JD_READ)")
    public ResponseEntity<JobDescriptionSummary> detail(@PathVariable UUID id) {
        UUID tenantId = currentUserService.currentTenantId();
        return jdRepository.findByIdAndTenantId(id, tenantId)
                .map(jd -> ResponseEntity.ok(toSummary(jd)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("@permissionGuard.has(T(com.walden.cvect.security.PermissionCodes).JD_WRITE)")
    public ResponseEntity<JobDescriptionSummary> create(@Valid @RequestBody JobDescriptionRequest request) {
        JobDescription saved = jobDescriptionApplicationService.create(request.title(), request.content());
        return ResponseEntity.ok(toSummary(saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permissionGuard.has(T(com.walden.cvect.security.PermissionCodes).JD_WRITE)")
    public ResponseEntity<JobDescriptionSummary> update(
            @PathVariable UUID id,
            @Valid @RequestBody JobDescriptionRequest request) {
        return jobDescriptionApplicationService.update(id, request.title(), request.content())
                .map(saved -> ResponseEntity.ok(toSummary(saved)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionGuard.has(T(com.walden.cvect.security.PermissionCodes).JD_DELETE)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        return jobDescriptionApplicationService.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    private JobDescriptionSummary toSummary(JobDescription jd) {
        return toSummary(jd, candidateRepository.countByTenantIdAndJobDescriptionId(
                currentUserService.currentTenantId(),
                jd.getId()));
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
