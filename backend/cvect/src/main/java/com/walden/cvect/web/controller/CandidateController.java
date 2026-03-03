package com.walden.cvect.web.controller;

import com.walden.cvect.model.entity.Candidate;
import com.walden.cvect.model.entity.CandidateRecruitmentStatus;
import com.walden.cvect.repository.CandidateJpaRepository;
import com.walden.cvect.repository.ResumeChunkVectorJpaRepository;
import com.walden.cvect.service.CandidateSnapshotService;
import com.walden.cvect.web.stream.CandidateStreamEvent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
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
    private final CandidateSnapshotService snapshotService;
    private final ResumeChunkVectorJpaRepository resumeChunkVectorRepository;

    public CandidateController(CandidateJpaRepository candidateRepository,
            CandidateSnapshotService snapshotService,
            ResumeChunkVectorJpaRepository resumeChunkVectorRepository) {
        this.candidateRepository = candidateRepository;
        this.snapshotService = snapshotService;
        this.resumeChunkVectorRepository = resumeChunkVectorRepository;
    }

    @GetMapping
    public ResponseEntity<List<CandidateListItem>> listByJd(@RequestParam("jdId") UUID jdId) {
        List<Candidate> candidates = candidateRepository.findByJobDescriptionIdOrderByCreatedAtDesc(jdId);
        List<CandidateStreamEvent> snapshotEvents = snapshotService.listByJd(jdId);
        Map<UUID, CandidateStreamEvent> byCandidateId = new HashMap<>();
        for (CandidateStreamEvent event : snapshotEvents) {
            byCandidateId.put(event.candidateId(), event);
        }

        Set<UUID> candidateIds = candidates.stream()
                .map(Candidate::getId)
                .collect(java.util.stream.Collectors.toSet());
        Set<UUID> vectorizedCandidateIds = candidateIds.isEmpty()
                ? Set.of()
                : new HashSet<>(resumeChunkVectorRepository.findDistinctCandidateIdsIn(candidateIds));

        List<CandidateListItem> events = new ArrayList<>();
        for (Candidate candidate : candidates) {
            CandidateStreamEvent event = byCandidateId.get(candidate.getId());
            if (event == null) {
                event = snapshotService.build(candidate.getId(), "DONE");
            }
            if (event == null) {
                continue;
            }
            boolean noVectorChunk = !vectorizedCandidateIds.contains(candidate.getId());
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
                    noVectorChunk));
        }
        return ResponseEntity.ok(events);
    }

    @PatchMapping("/{id}/recruitment-status")
    public ResponseEntity<CandidateStreamEvent> updateRecruitmentStatus(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateRecruitmentStatusRequest request) {
        Candidate candidate = candidateRepository.findById(id).orElse(null);
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
            boolean noVectorChunk
    ) {
    }
}
