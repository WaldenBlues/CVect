package com.walden.cvect.web.controller.candidate;

import com.walden.cvect.model.entity.Candidate;
import com.walden.cvect.model.entity.CandidateRecruitmentStatus;
import com.walden.cvect.model.entity.CandidateMatchScore;
import com.walden.cvect.model.entity.vector.VectorIngestTaskStatus;
import com.walden.cvect.logging.aop.AuditAction;
import com.walden.cvect.repository.CandidateMatchScoreJpaRepository;
import com.walden.cvect.repository.CandidateJpaRepository;
import com.walden.cvect.repository.ResumeChunkVectorJpaRepository;
import com.walden.cvect.repository.VectorIngestTaskJpaRepository;
import com.walden.cvect.security.CurrentUserService;
import com.walden.cvect.security.DataScopeService;
import com.walden.cvect.service.candidate.CandidateSnapshotService;
import com.walden.cvect.service.matching.PersistedMatchScoreService;
import com.walden.cvect.web.stream.CandidateStreamEvent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 候选人查询 API
 */
@RestController
@RequestMapping("/api/candidates")
public class CandidateController {
    private static final Logger log = LoggerFactory.getLogger(CandidateController.class);

    private final CandidateJpaRepository candidateRepository;
    private final CandidateMatchScoreJpaRepository candidateMatchScoreRepository;
    private final CandidateSnapshotService snapshotService;
    private final ResumeChunkVectorJpaRepository resumeChunkVectorRepository;
    private final VectorIngestTaskJpaRepository vectorIngestTaskRepository;
    private final PersistedMatchScoreService persistedMatchScoreService;
    private final CurrentUserService currentUserService;
    private final DataScopeService dataScopeService;

    public CandidateController(CandidateJpaRepository candidateRepository,
            CandidateMatchScoreJpaRepository candidateMatchScoreRepository,
            CandidateSnapshotService snapshotService,
            ResumeChunkVectorJpaRepository resumeChunkVectorRepository,
            VectorIngestTaskJpaRepository vectorIngestTaskRepository,
            PersistedMatchScoreService persistedMatchScoreService,
            CurrentUserService currentUserService,
            DataScopeService dataScopeService) {
        this.candidateRepository = candidateRepository;
        this.candidateMatchScoreRepository = candidateMatchScoreRepository;
        this.snapshotService = snapshotService;
        this.resumeChunkVectorRepository = resumeChunkVectorRepository;
        this.vectorIngestTaskRepository = vectorIngestTaskRepository;
        this.persistedMatchScoreService = persistedMatchScoreService;
        this.currentUserService = currentUserService;
        this.dataScopeService = dataScopeService;
    }

    @GetMapping
    @PreAuthorize("@permissionGuard.has(T(com.walden.cvect.security.PermissionCodes).CANDIDATE_READ)")
    public ResponseEntity<List<CandidateListItem>> listByJd(@RequestParam("jdId") UUID jdId) {
        UUID tenantId = currentUserService.currentTenantId();
        List<Candidate> candidates = visibleCandidates(tenantId, jdId);
        List<CandidateStreamEvent> snapshotEvents = snapshotService.listByTenantAndJd(tenantId, jdId);
        Map<UUID, CandidateStreamEvent> byCandidateId = new HashMap<>();
        for (CandidateStreamEvent event : snapshotEvents) {
            byCandidateId.put(event.candidateId(), event);
        }

        Set<UUID> candidateIds = candidates.stream()
                .map(Candidate::getId)
                .collect(java.util.stream.Collectors.toSet());
        Map<UUID, CandidateMatchScore> scoreByCandidateId = candidateIds.isEmpty()
                ? Map.of()
                : candidateMatchScoreRepository.findByTenantIdAndJobDescriptionIdAndCandidateIdIn(
                                tenantId,
                                jdId,
                                candidateIds).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                CandidateMatchScore::getCandidateId,
                                score -> score,
                                (left, right) -> right));
        Set<UUID> vectorizedCandidateIds = candidateIds.isEmpty()
                ? Set.of()
                : new HashSet<>(resumeChunkVectorRepository.findDistinctCandidateIdsIn(candidateIds));
        if (!vectorizedCandidateIds.isEmpty()
                && !scoreByCandidateId.keySet().containsAll(vectorizedCandidateIds)) {
            persistedMatchScoreService.scheduleRefreshForJobDescription(jdId);
        }
        Set<UUID> inflightCandidateIds = candidateIds.isEmpty()
                ? Set.of()
                : new HashSet<>(vectorIngestTaskRepository.findCandidateIdsByStatusIn(
                        candidateIds,
                        List.of(VectorIngestTaskStatus.PENDING, VectorIngestTaskStatus.PROCESSING)));
        Set<UUID> failedCandidateIds = candidateIds.isEmpty()
                ? Set.of()
                : new HashSet<>(vectorIngestTaskRepository.findCandidateIdsByStatusIn(
                        candidateIds,
                        List.of(VectorIngestTaskStatus.FAILED)));

        List<CandidateListItem> events = new ArrayList<>();
        for (Candidate candidate : candidates) {
            CandidateStreamEvent event = byCandidateId.get(candidate.getId());
            if (event == null) {
                event = snapshotService.build(candidate.getId(), "DONE");
            }
            if (event == null) {
                continue;
            }
            boolean hasVectorChunk = vectorizedCandidateIds.contains(candidate.getId());
            boolean hasInflight = inflightCandidateIds.contains(candidate.getId());
            boolean hasFailed = failedCandidateIds.contains(candidate.getId());
            CandidateMatchScore matchScore = scoreByCandidateId.get(candidate.getId());
            String vectorStatus = resolveVectorStatus(hasVectorChunk, hasInflight, hasFailed);
            boolean noVectorChunk = !hasVectorChunk;
            events.add(new CandidateListItem(
                    event.candidateId(),
                    event.jdId(),
                    event.status(),
                    event.recruitmentStatus(),
                    event.name(),
                    event.sourceFileName(),
                    event.contentType(),
                    event.fileSizeBytes(),
                    event.parsedCharCount(),
                    event.truncated(),
                    event.createdAt(),
                    event.emails(),
                    event.phones(),
                    event.educations(),
                    event.honors(),
                    event.links(),
                    matchScore == null ? null : matchScore.getOverallScore(),
                    matchScore == null ? null : matchScore.getExperienceScore(),
                    matchScore == null ? null : matchScore.getSkillScore(),
                    matchScore == null ? null : matchScore.getScoredAt(),
                    vectorStatus,
                    noVectorChunk));
        }
        return ResponseEntity.ok(events);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permissionGuard.has(T(com.walden.cvect.security.PermissionCodes).CANDIDATE_READ)")
    public ResponseEntity<CandidateSnapshotService.CandidateDetailView> getById(@PathVariable("id") UUID id) {
        UUID tenantId = currentUserService.currentTenantId();
        Candidate candidate = findVisibleCandidate(id, tenantId).orElse(null);
        if (candidate == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(snapshotService.buildDetail(candidate));
    }

    private List<Candidate> visibleCandidates(UUID tenantId, UUID jdId) {
        if (dataScopeService.hasTenantWideScope()) {
            return candidateRepository.findByTenantIdAndJobDescriptionIdOrderByCreatedAtDesc(tenantId, jdId);
        }
        UUID userId = dataScopeService.currentUserIdOrNull();
        if (userId == null) {
            return List.of();
        }
        return candidateRepository.findByTenantIdAndJobDescriptionIdAndJobDescriptionCreatedByUserIdOrderByCreatedAtDesc(
                tenantId,
                jdId,
                userId);
    }

    private java.util.Optional<Candidate> findVisibleCandidate(UUID candidateId, UUID tenantId) {
        if (dataScopeService.hasTenantWideScope()) {
            return candidateRepository.findByIdAndTenantId(candidateId, tenantId);
        }
        UUID userId = dataScopeService.currentUserIdOrNull();
        if (userId == null) {
            return java.util.Optional.empty();
        }
        return candidateRepository.findByIdAndTenantIdAndJobDescriptionCreatedByUserId(candidateId, tenantId, userId);
    }

    private static String resolveVectorStatus(boolean hasVectorChunk, boolean hasInflight, boolean hasFailed) {
        if (hasInflight && hasVectorChunk) {
            return "PARTIAL";
        }
        if (hasInflight) {
            return "PROCESSING";
        }
        if (hasVectorChunk && hasFailed) {
            return "PARTIAL";
        }
        if (hasVectorChunk) {
            return "READY";
        }
        if (hasFailed) {
            return "FAILED";
        }
        return "NONE";
    }

    @PatchMapping("/{id}/recruitment-status")
    @PreAuthorize("@permissionGuard.has(T(com.walden.cvect.security.PermissionCodes).CANDIDATE_UPDATE_STATUS)")
    @AuditAction(action = "update_candidate_recruitment_status", target = "candidate", logResult = true)
    public ResponseEntity<CandidateStreamEvent> updateRecruitmentStatus(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateRecruitmentStatusRequest request) {
        UUID tenantId = currentUserService.currentTenantId();
        Candidate candidate = findVisibleCandidate(id, tenantId).orElse(null);
        if (candidate == null) {
            return ResponseEntity.notFound().build();
        }

        candidate.setRecruitmentStatus(request.recruitmentStatus());
        Candidate saved = candidateRepository.save(candidate);
        CandidateStreamEvent event;
        try {
            event = snapshotService.build(saved.getId(), "UPDATED");
        } catch (RuntimeException ex) {
            log.warn("Failed building candidate snapshot after recruitment status update, fallback response: candidateId={}",
                    saved.getId(), ex);
            event = null;
        }
        if (event == null) {
            event = fallbackUpdatedEvent(saved);
        }
        return ResponseEntity.ok(event);
    }

    private CandidateStreamEvent fallbackUpdatedEvent(Candidate candidate) {
        if (candidate == null) {
            return null;
        }
        return new CandidateStreamEvent(
                candidate.getId(),
                candidate.getTenantId(),
                candidate.getJobDescription() == null ? null : candidate.getJobDescription().getId(),
                "UPDATED",
                candidate.getRecruitmentStatus() == null
                        ? CandidateRecruitmentStatus.TO_CONTACT.name()
                        : candidate.getRecruitmentStatus().name(),
                candidate.getName(),
                candidate.getSourceFileName(),
                candidate.getContentType(),
                candidate.getFileSizeBytes(),
                candidate.getParsedCharCount(),
                candidate.getTruncated(),
                candidate.getCreatedAt(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    public record UpdateRecruitmentStatusRequest(
            @NotNull CandidateRecruitmentStatus recruitmentStatus
    ) {
    }

    public record CandidateListItem(
            UUID candidateId,
            UUID jdId,
            String status,
            String recruitmentStatus,
            String name,
            String sourceFileName,
            String contentType,
            Long fileSizeBytes,
            Integer parsedCharCount,
            Boolean truncated,
            java.time.LocalDateTime createdAt,
            List<String> emails,
            List<String> phones,
            List<String> educations,
            List<String> honors,
            List<String> links,
            Float baselineMatchScore,
            Float baselineExperienceScore,
            Float baselineSkillScore,
            java.time.LocalDateTime baselineScoredAt,
            String vectorStatus,
            boolean noVectorChunk
    ) {
    }
}
