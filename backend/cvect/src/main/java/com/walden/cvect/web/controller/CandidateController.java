package com.walden.cvect.web.controller;

import com.walden.cvect.model.entity.Candidate;
import com.walden.cvect.model.entity.CandidateRecruitmentStatus;
import com.walden.cvect.repository.CandidateJpaRepository;
import com.walden.cvect.service.CandidateSnapshotService;
import com.walden.cvect.web.stream.CandidateStreamEvent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 候选人查询 API
 */
@RestController
@RequestMapping("/api/candidates")
public class CandidateController {

    private final CandidateJpaRepository candidateRepository;
    private final CandidateSnapshotService snapshotService;

    public CandidateController(CandidateJpaRepository candidateRepository,
            CandidateSnapshotService snapshotService) {
        this.candidateRepository = candidateRepository;
        this.snapshotService = snapshotService;
    }

    @GetMapping
    public ResponseEntity<List<CandidateStreamEvent>> listByJd(@RequestParam("jdId") UUID jdId) {
        List<Candidate> candidates = candidateRepository.findByJobDescriptionIdOrderByCreatedAtDesc(jdId);
        List<CandidateStreamEvent> snapshotEvents = snapshotService.listByJd(jdId);
        Map<UUID, CandidateStreamEvent> byCandidateId = new HashMap<>();
        for (CandidateStreamEvent event : snapshotEvents) {
            byCandidateId.put(event.candidateId(), event);
        }

        List<CandidateStreamEvent> events = new ArrayList<>();
        for (Candidate candidate : candidates) {
            CandidateStreamEvent event = byCandidateId.get(candidate.getId());
            if (event == null) {
                event = snapshotService.build(candidate.getId(), "DONE");
            }
            if (event == null) {
                continue;
            }
            events.add(event);
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
        CandidateStreamEvent event = snapshotService.build(saved.getId(), "UPDATED");
        if (event == null) {
            return ResponseEntity.status(500).build();
        }
        return ResponseEntity.ok(event);
    }

    public record UpdateRecruitmentStatusRequest(
            @NotNull CandidateRecruitmentStatus recruitmentStatus
    ) {
    }
}
