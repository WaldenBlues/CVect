package com.walden.cvect.service.candidate;

import com.walden.cvect.model.entity.Candidate;
import com.walden.cvect.model.entity.CandidateMatchScore;
import com.walden.cvect.model.entity.CandidateRecruitmentStatus;
import com.walden.cvect.model.entity.CandidateSnapshot;
import com.walden.cvect.model.entity.Contact;
import com.walden.cvect.model.entity.Education;
import com.walden.cvect.model.entity.Honor;
import com.walden.cvect.model.entity.Link;
import com.walden.cvect.repository.CandidateJpaRepository;
import com.walden.cvect.repository.CandidateMatchScoreJpaRepository;
import com.walden.cvect.repository.CandidateSnapshotJpaRepository;
import com.walden.cvect.repository.ContactJpaRepository;
import com.walden.cvect.repository.EducationJpaRepository;
import com.walden.cvect.repository.HonorJpaRepository;
import com.walden.cvect.repository.LinkJpaRepository;
import com.walden.cvect.web.stream.CandidateStreamEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 组装候选人入库快照
 */
@Service
public class CandidateSnapshotService {
    private static final Logger log = LoggerFactory.getLogger(CandidateSnapshotService.class);
    private static final String DEFAULT_EVENT_STATUS = "DONE";


    private final CandidateJpaRepository candidateRepository;
    private final CandidateMatchScoreJpaRepository candidateMatchScoreRepository;
    private final CandidateSnapshotJpaRepository snapshotRepository;
    private final ContactJpaRepository contactRepository;
    private final EducationJpaRepository educationRepository;
    private final HonorJpaRepository honorRepository;
    private final LinkJpaRepository linkRepository;
    private final ObjectMapper objectMapper;

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    public CandidateSnapshotService(
            CandidateJpaRepository candidateRepository,
            CandidateMatchScoreJpaRepository candidateMatchScoreRepository,
            CandidateSnapshotJpaRepository snapshotRepository,
            ContactJpaRepository contactRepository,
            EducationJpaRepository educationRepository,
            HonorJpaRepository honorRepository,
            LinkJpaRepository linkRepository,
            ObjectMapper objectMapper) {
        this.candidateRepository = candidateRepository;
        this.candidateMatchScoreRepository = candidateMatchScoreRepository;
        this.snapshotRepository = snapshotRepository;
        this.contactRepository = contactRepository;
        this.educationRepository = educationRepository;
        this.honorRepository = honorRepository;
        this.linkRepository = linkRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<CandidateStreamEvent> listByJd(UUID jdId) {
        return listByTenantAndJd(null, jdId);
    }

    @Transactional(readOnly = true)
    public List<CandidateStreamEvent> listByTenantAndJd(UUID tenantId, UUID jdId) {
        if (jdId == null) {
            return List.of();
        }
        var snapshots = tenantId == null
                ? snapshotRepository.findByJdIdOrderByCandidateCreatedAtDesc(jdId)
                : snapshotRepository.findByTenantIdAndJdIdOrderByCandidateCreatedAtDesc(tenantId, jdId);
        return snapshots.stream()
                .map(snapshot -> toEvent(snapshot, DEFAULT_EVENT_STATUS))
                .toList();
    }

    /**
     * 构建入库快照（用于实时推送）
     */
    public CandidateStreamEvent build(UUID candidateId) {
        return build(candidateId, DEFAULT_EVENT_STATUS);
    }

    public CandidateStreamEvent build(UUID candidateId, String status) {
        if (candidateId == null) {
            return null;
        }

        Candidate candidate = candidateRepository.findById(candidateId).orElse(null);
        if (candidate == null) {
            return null;
        }

        CandidateDetailCollections detailCollections = loadDetailCollections(candidateId);

        CandidateStreamEvent event = new CandidateStreamEvent(
                candidate.getId(),
                candidate.getTenantId(),
                candidate.getJobDescription() == null ? null : candidate.getJobDescription().getId(),
                status,
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
                detailCollections.emails(),
                detailCollections.phones(),
                detailCollections.education(),
                detailCollections.honor(),
                detailCollections.externalLinks()
        );
        upsertSnapshot(event);
        return event;
    }

    @Transactional(readOnly = true)
    public CandidateDetailView buildDetail(Candidate candidate) {
        if (candidate == null) {
            return null;
        }

        CandidateDetailCollections detailCollections = loadDetailCollections(candidate.getId());
        PersistedMatchScoreView persistedMatchScore = resolvePersistedMatchScore(candidate);

        return new CandidateDetailView(
                candidate.getId(),
                candidate.getName(),
                new ContactInfoView(detailCollections.emails(), detailCollections.phones()),
                detailCollections.education(),
                detailCollections.honor(),
                detailCollections.externalLinks(),
                new UploadFileMetadataView(
                        candidate.getSourceFileName(),
                        candidate.getContentType(),
                        candidate.getFileSizeBytes(),
                        candidate.getParsedCharCount(),
                        candidate.getTruncated()),
                candidate.getRecruitmentStatus() == null
                        ? CandidateRecruitmentStatus.TO_CONTACT.name()
                        : candidate.getRecruitmentStatus().name(),
                persistedMatchScore);
    }

    private void upsertSnapshot(CandidateStreamEvent event) {
        if (event == null || event.candidateId() == null) {
            return;
        }
        CandidateSnapshot snapshot = snapshotRepository.findById(event.candidateId())
                .orElseGet(() -> new CandidateSnapshot(event.tenantId(), event.candidateId()));
        snapshot.setTenantId(event.tenantId());
        snapshot.setJdId(event.jdId());
        snapshot.setRecruitmentStatus(event.recruitmentStatus());
        snapshot.setName(event.name());
        snapshot.setSourceFileName(event.sourceFileName());
        snapshot.setContentType(event.contentType());
        snapshot.setFileSizeBytes(event.fileSizeBytes());
        snapshot.setParsedCharCount(event.parsedCharCount());
        snapshot.setTruncated(event.truncated());
        snapshot.setCandidateCreatedAt(event.createdAt());
        snapshot.setEmailsJson(serializeList(event.emails()));
        snapshot.setPhonesJson(serializeList(event.phones()));
        snapshot.setEducationsJson(serializeList(event.educations()));
        snapshot.setHonorsJson(serializeList(event.honors()));
        snapshot.setLinksJson(serializeList(event.links()));
        snapshotRepository.save(snapshot);
    }

    private CandidateStreamEvent toEvent(CandidateSnapshot snapshot, String status) {
        return new CandidateStreamEvent(
                snapshot.getCandidateId(),
                snapshot.getTenantId(),
                snapshot.getJdId(),
                status,
                snapshot.getRecruitmentStatus(),
                snapshot.getName(),
                snapshot.getSourceFileName(),
                snapshot.getContentType(),
                snapshot.getFileSizeBytes(),
                snapshot.getParsedCharCount(),
                snapshot.getTruncated(),
                snapshot.getCandidateCreatedAt(),
                deserializeList(snapshot.getEmailsJson()),
                deserializeList(snapshot.getPhonesJson()),
                deserializeList(snapshot.getEducationsJson()),
                deserializeList(snapshot.getHonorsJson()),
                deserializeList(snapshot.getLinksJson()));
    }

    private CandidateDetailCollections loadDetailCollections(UUID candidateId) {
        List<Contact> contacts = contactRepository.findByCandidateId(candidateId);
        List<Education> educations = educationRepository.findByCandidateId(candidateId);
        List<Honor> honors = honorRepository.findByCandidateId(candidateId);
        List<Link> links = linkRepository.findByCandidateId(candidateId);

        List<String> emails = contacts.stream()
                .filter(contact -> "EMAIL".equalsIgnoreCase(contact.getType()))
                .map(Contact::getValue)
                .toList();
        List<String> phones = contacts.stream()
                .filter(contact -> "PHONE".equalsIgnoreCase(contact.getType()))
                .map(Contact::getValue)
                .toList();
        List<String> educationTexts = educations.stream()
                .map(Education::getSchool)
                .toList();
        List<String> honorTexts = honors.stream()
                .map(Honor::getContent)
                .toList();
        List<String> externalLinks = links.stream()
                .map(Link::getUrl)
                .toList();
        return new CandidateDetailCollections(emails, phones, educationTexts, honorTexts, externalLinks);
    }

    private PersistedMatchScoreView resolvePersistedMatchScore(Candidate candidate) {
        UUID candidateId = candidate.getId();
        UUID tenantId = candidate.getTenantId();
        UUID jobDescriptionId = candidate.getJobDescription() == null ? null : candidate.getJobDescription().getId();
        if (candidateId == null || tenantId == null || jobDescriptionId == null) {
            return null;
        }
        CandidateMatchScore matchScore = candidateMatchScoreRepository.findByTenantIdAndCandidateIdAndJobDescriptionId(
                tenantId,
                candidateId,
                jobDescriptionId).orElse(null);
        if (matchScore == null) {
            return null;
        }
        return new PersistedMatchScoreView(matchScore.getOverallScore(), matchScore.getScoredAt());
    }

    private String serializeList(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize candidate snapshot list", e);
            return "[]";
        }
    }

    private List<String> deserializeList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, STRING_LIST_TYPE);
        } catch (Exception e) {
            log.warn("Failed to deserialize candidate snapshot list payload", e);
            return List.of();
        }
    }

    private record CandidateDetailCollections(
            List<String> emails,
            List<String> phones,
            List<String> education,
            List<String> honor,
            List<String> externalLinks) {
    }

    public record CandidateDetailView(
            UUID candidateId,
            String name,
            ContactInfoView contact,
            List<String> education,
            List<String> honor,
            List<String> externalLinks,
            UploadFileMetadataView uploadFile,
            String recruitmentStatus,
            PersistedMatchScoreView persistedMatchScore) {
    }

    public record ContactInfoView(
            List<String> emails,
            List<String> phones) {
    }

    public record UploadFileMetadataView(
            String sourceFileName,
            String contentType,
            Long fileSizeBytes,
            Integer parsedCharCount,
            Boolean truncated) {
    }

    public record PersistedMatchScoreView(
            Float overallScore,
            java.time.LocalDateTime scoredAt) {
    }
}
