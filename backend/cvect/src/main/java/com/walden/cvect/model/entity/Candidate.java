package com.walden.cvect.model.entity;

import com.walden.cvect.model.TenantConstants;
import jakarta.persistence.*;
import org.hibernate.annotations.Check;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 候选人基础信息实体
 * 仅存储解析元数据，不持久化 EXPERIENCE/SKILL 细节
 */
@Entity
@Table(
        name = "candidates",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_candidates_tenant_file_hash_jd",
                columnNames = { "tenant_id", "file_hash", "jd_id" }),
        indexes = {
                @Index(name = "idx_candidates_jd_id", columnList = "jd_id"),
                @Index(name = "idx_candidates_tenant_jd_created", columnList = "tenant_id,jd_id,created_at")
        }
)
@Check(constraints = "recruitment_status in ('TO_CONTACT','TO_INTERVIEW','REJECTED')")
public class Candidate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "source_file_name", columnDefinition = "TEXT")
    private String sourceFileName;

    @Column(name = "file_hash", length = 64)
    private String fileHash;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "content_type", columnDefinition = "TEXT")
    private String contentType;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "parsed_char_count")
    private Integer parsedCharCount;

    @Column(name = "truncated")
    private Boolean truncated;

    @Enumerated(EnumType.STRING)
    @Column(name = "recruitment_status", length = 32)
    private CandidateRecruitmentStatus recruitmentStatus;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jd_id", foreignKey = @ForeignKey(name = "fk_candidates_jd"))
    private JobDescription jobDescription;

    protected Candidate() {
    }

    public Candidate(String sourceFileName,
            String fileHash,
            String name,
            JobDescription jobDescription,
            String contentType,
            Long fileSizeBytes,
            Integer parsedCharCount,
            Boolean truncated) {
        this(jobDescription == null ? TenantConstants.DEFAULT_TENANT_ID : jobDescription.getTenantId(),
                sourceFileName,
                fileHash,
                name,
                jobDescription,
                contentType,
                fileSizeBytes,
                parsedCharCount,
                truncated);
    }

    public Candidate(UUID tenantId,
            String sourceFileName,
            String fileHash,
            String name,
            JobDescription jobDescription,
            String contentType,
            Long fileSizeBytes,
            Integer parsedCharCount,
            Boolean truncated) {
        this.tenantId = tenantId == null ? TenantConstants.DEFAULT_TENANT_ID : tenantId;
        this.sourceFileName = sourceFileName;
        this.fileHash = fileHash;
        this.name = name;
        this.jobDescription = jobDescription;
        this.contentType = contentType;
        this.fileSizeBytes = fileSizeBytes;
        this.parsedCharCount = parsedCharCount;
        this.truncated = truncated;
        this.recruitmentStatus = CandidateRecruitmentStatus.TO_CONTACT;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.tenantId == null) {
            this.tenantId = jobDescription == null ? TenantConstants.DEFAULT_TENANT_ID : jobDescription.getTenantId();
        }
        if (this.recruitmentStatus == null) {
            this.recruitmentStatus = CandidateRecruitmentStatus.TO_CONTACT;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public String getFileHash() {
        return fileHash;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getContentType() {
        return contentType;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public Integer getParsedCharCount() {
        return parsedCharCount;
    }

    public Boolean getTruncated() {
        return truncated;
    }

    public CandidateRecruitmentStatus getRecruitmentStatus() {
        return recruitmentStatus;
    }

    public void setRecruitmentStatus(CandidateRecruitmentStatus recruitmentStatus) {
        this.recruitmentStatus = recruitmentStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public JobDescription getJobDescription() {
        return jobDescription;
    }

    public void setJobDescription(JobDescription jobDescription) {
        this.jobDescription = jobDescription;
        if (jobDescription != null) {
            this.tenantId = jobDescription.getTenantId();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Candidate candidate = (Candidate) o;
        return Objects.equals(id, candidate.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
