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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 50;
    private static final int MAX_SIZE = 100;
    private static final String HEADER_TOTAL_COUNT = "X-Total-Count";
    private static final String HEADER_PAGE = "X-Page";
    private static final String HEADER_PAGE_SIZE = "X-Page-Size";
    private static final String HEADER_TOTAL_PAGES = "X-Total-Pages";
    private static final String HEADER_HAS_NEXT = "X-Has-Next";
    private static final String HEADER_HAS_PREVIOUS = "X-Has-Previous";

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

    public ResponseEntity<List<CandidateListItem>> listByJd(UUID jdId) {
        return listByJd(jdId, DEFAULT_PAGE, DEFAULT_SIZE, null, null);
    }

    @GetMapping
    @PreAuthorize("@permissionGuard.has(T(com.walden.cvect.security.PermissionCodes).CANDIDATE_READ)")
    public ResponseEntity<List<CandidateListItem>> listByJd(
            @RequestParam("jdId") UUID jdId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size,
            @RequestParam(value = "q", required = false) String keyword,
            @RequestParam(value = "recruitmentStatus", required = false) CandidateRecruitmentStatus recruitmentStatus) {
        UUID tenantId = currentUserService.currentTenantId();
        PageRequest pageRequest = PageRequest.of(normalizePage(page), normalizeSize(size));
        Page<Candidate> candidatePage = visibleCandidates(tenantId, jdId, keyword, recruitmentStatus, pageRequest);
        List<Candidate> candidates = candidatePage.getContent();

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
        CandidateVectorState vectorState = loadVectorState(candidateIds);
        if (!vectorState.vectorizedCandidateIds().isEmpty()
                && !scoreByCandidateId.keySet().containsAll(vectorState.vectorizedCandidateIds())) {
            persistedMatchScoreService.scheduleRefreshForJobDescription(jdId);
        }

        List<CandidateListItem> items = candidates.stream()
                .map(candidate -> toListItem(candidate, jdId, scoreByCandidateId, vectorState))
                .toList();

        return ResponseEntity.ok()
                .header(HEADER_TOTAL_COUNT, String.valueOf(candidatePage.getTotalElements()))
                .header(HEADER_PAGE, String.valueOf(candidatePage.getNumber()))
                .header(HEADER_PAGE_SIZE, String.valueOf(candidatePage.getSize()))
                .header(HEADER_TOTAL_PAGES, String.valueOf(candidatePage.getTotalPages()))
                .header(HEADER_HAS_NEXT, String.valueOf(candidatePage.hasNext()))
                .header(HEADER_HAS_PREVIOUS, String.valueOf(candidatePage.hasPrevious()))
                .body(items);
    }

    @GetMapping("/vector-status")
    @PreAuthorize("@permissionGuard.has(T(com.walden.cvect.security.PermissionCodes).CANDIDATE_READ)")
    public ResponseEntity<List<CandidateVectorStatusItem>> listVectorStatuses(
            @RequestParam("candidateId") List<UUID> candidateIds) {
        if (candidateIds == null || candidateIds.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        UUID tenantId = currentUserService.currentTenantId();
        List<UUID> visibleCandidateIds = visibleCandidateIds(tenantId, candidateIds);
        if (visibleCandidateIds.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        Set<UUID> visibleCandidateIdSet = new LinkedHashSet<>(visibleCandidateIds);
        CandidateVectorState vectorState = loadVectorState(visibleCandidateIdSet);
        List<CandidateVectorStatusItem> items = candidateIds.stream()
                .filter(visibleCandidateIdSet::contains)
                .distinct()
                .map(candidateId -> toVectorStatusItem(candidateId, vectorState))
                .toList();
        return ResponseEntity.ok(items);
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

    private Page<Candidate> visibleCandidates(
            UUID tenantId,
            UUID jdId,
            String keyword,
            CandidateRecruitmentStatus recruitmentStatus,
            PageRequest pageRequest) {
        String normalizedKeyword = normalizeKeyword(keyword);
        boolean hasFilters = normalizedKeyword != null || recruitmentStatus != null;
        if (dataScopeService.hasTenantWideScope()) {
            if (!hasFilters) {
                return candidateRepository.findByTenantIdAndJobDescriptionIdOrderByCreatedAtDesc(tenantId, jdId, pageRequest);
            }
            return candidateRepository.searchByTenantIdAndJobDescriptionIdOrderByCreatedAtDesc(
                    tenantId,
                    jdId,
                    recruitmentStatus,
                    normalizedKeyword,
                    pageRequest);
        }
        UUID userId = dataScopeService.currentUserIdOrNull();
        if (userId == null) {
            return Page.empty(pageRequest);
        }
        if (!hasFilters) {
            return candidateRepository.findByTenantIdAndJobDescriptionIdAndJobDescriptionCreatedByUserIdOrderByCreatedAtDesc(
                    tenantId,
                    jdId,
                    userId,
                    pageRequest);
        }
        return candidateRepository.searchByTenantIdAndJobDescriptionIdAndJobDescriptionCreatedByUserIdOrderByCreatedAtDesc(
                tenantId,
                jdId,
                userId,
                recruitmentStatus,
                normalizedKeyword,
                pageRequest);
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

    private List<UUID> visibleCandidateIds(UUID tenantId, List<UUID> candidateIds) {
        if (candidateIds == null || candidateIds.isEmpty()) {
            return List.of();
        }
        if (dataScopeService.hasTenantWideScope()) {
            return candidateRepository.findVisibleIdsByTenantIdAndIdIn(tenantId, candidateIds);
        }
        UUID userId = dataScopeService.currentUserIdOrNull();
        if (userId == null) {
            return List.of();
        }
        return candidateRepository.findVisibleIdsByTenantIdAndIdInAndJobDescriptionCreatedByUserId(
                tenantId,
                candidateIds,
                userId);
    }

    private CandidateVectorState loadVectorState(Set<UUID> candidateIds) {
        if (candidateIds == null || candidateIds.isEmpty()) {
            return CandidateVectorState.empty();
        }
        Set<UUID> vectorizedCandidateIds = new HashSet<>(resumeChunkVectorRepository.findDistinctCandidateIdsIn(candidateIds));
        Set<UUID> inflightCandidateIds = new HashSet<>(vectorIngestTaskRepository.findCandidateIdsByStatusIn(
                candidateIds,
                List.of(VectorIngestTaskStatus.PENDING, VectorIngestTaskStatus.PROCESSING)));
        Set<UUID> failedCandidateIds = new HashSet<>(vectorIngestTaskRepository.findCandidateIdsByStatusIn(
                candidateIds,
                List.of(VectorIngestTaskStatus.FAILED)));
        return new CandidateVectorState(vectorizedCandidateIds, inflightCandidateIds, failedCandidateIds);
    }

    private CandidateListItem toListItem(
            Candidate candidate,
            UUID jdId,
            Map<UUID, CandidateMatchScore> scoreByCandidateId,
            CandidateVectorState vectorState) {
        UUID candidateId = candidate.getId();
        boolean hasVectorChunk = vectorState.vectorizedCandidateIds().contains(candidateId);
        boolean hasInflight = vectorState.inflightCandidateIds().contains(candidateId);
        boolean hasFailed = vectorState.failedCandidateIds().contains(candidateId);
        CandidateMatchScore matchScore = scoreByCandidateId.get(candidateId);
        String recruitmentStatus = candidate.getRecruitmentStatus() == null
                ? CandidateRecruitmentStatus.TO_CONTACT.name()
                : candidate.getRecruitmentStatus().name();
        return new CandidateListItem(
                candidateId,
                jdId,
                recruitmentStatus,
                candidate.getName(),
                candidate.getSourceFileName(),
                candidate.getCreatedAt(),
                matchScore == null ? null : matchScore.getOverallScore(),
                resolveVectorStatus(hasVectorChunk, hasInflight, hasFailed),
                !hasVectorChunk);
    }

    private CandidateVectorStatusItem toVectorStatusItem(UUID candidateId, CandidateVectorState vectorState) {
        boolean hasVectorChunk = vectorState.vectorizedCandidateIds().contains(candidateId);
        boolean hasInflight = vectorState.inflightCandidateIds().contains(candidateId);
        boolean hasFailed = vectorState.failedCandidateIds().contains(candidateId);
        return new CandidateVectorStatusItem(
                candidateId,
                resolveVectorStatus(hasVectorChunk, hasInflight, hasFailed),
                !hasVectorChunk);
    }

    private static String normalizeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private static int normalizePage(int page) {
        return Math.max(DEFAULT_PAGE, page);
    }

    private static int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
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
            String recruitmentStatus,
            String name,
            String sourceFileName,
            java.time.LocalDateTime createdAt,
            Float baselineMatchScore,
            String vectorStatus,
            boolean noVectorChunk
    ) {
    }

    public record CandidateVectorStatusItem(
            UUID candidateId,
            String vectorStatus,
            boolean noVectorChunk
    ) {
    }

    private record CandidateVectorState(
            Set<UUID> vectorizedCandidateIds,
            Set<UUID> inflightCandidateIds,
            Set<UUID> failedCandidateIds
    ) {
        private static CandidateVectorState empty() {
            return new CandidateVectorState(Set.of(), Set.of(), Set.of());
        }
    }
}
