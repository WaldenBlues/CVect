package com.walden.cvect.model.entity;

import com.walden.cvect.model.TenantConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "candidate_snapshots", indexes = {
        @Index(name = "idx_candidate_snapshots_jd", columnList = "jd_id"),
        @Index(name = "idx_candidate_snapshots_tenant_jd", columnList = "tenant_id,jd_id")
})
public class CandidateSnapshot {

    @Id
    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(optional = false)
    @JoinColumn(
            name = "candidate_id",
            nullable = false,
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_candidate_snapshots_candidate"))
    private Candidate candidate;

    @Column(name = "jd_id")
    private UUID jdId;

    @Column(name = "recruitment_status", length = 32, nullable = false)
    private String recruitmentStatus;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "source_file_name", columnDefinition = "TEXT")
    private String sourceFileName;

    @Column(name = "content_type", columnDefinition = "TEXT")
    private String contentType;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "parsed_char_count")
    private Integer parsedCharCount;

    @Column(name = "truncated")
    private Boolean truncated;

    @Column(name = "candidate_created_at")
    private LocalDateTime candidateCreatedAt;

    @Column(name = "emails_json", columnDefinition = "TEXT")
    private String emailsJson;

    @Column(name = "phones_json", columnDefinition = "TEXT")
    private String phonesJson;

    @Column(name = "educations_json", columnDefinition = "TEXT")
    private String educationsJson;

    @Column(name = "honors_json", columnDefinition = "TEXT")
    private String honorsJson;

    @Column(name = "links_json", columnDefinition = "TEXT")
    private String linksJson;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected CandidateSnapshot() {
    }

    public CandidateSnapshot(UUID candidateId) {
        this(TenantConstants.DEFAULT_TENANT_ID, candidateId);
    }

    public CandidateSnapshot(UUID tenantId, UUID candidateId) {
        this.tenantId = tenantId == null ? TenantConstants.DEFAULT_TENANT_ID : tenantId;
        this.candidateId = candidateId;
    }

    @PrePersist
    void onCreate() {
        if (this.tenantId == null) {
            this.tenantId = TenantConstants.DEFAULT_TENANT_ID;
        }
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public UUID getCandidateId() {
        return candidateId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId == null ? TenantConstants.DEFAULT_TENANT_ID : tenantId;
    }

    public void setJdId(UUID jdId) {
        this.jdId = jdId;
    }

    public UUID getJdId() {
        return jdId;
    }

    public void setRecruitmentStatus(String recruitmentStatus) {
        this.recruitmentStatus = recruitmentStatus;
    }

    public String getRecruitmentStatus() {
        return recruitmentStatus;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setSourceFileName(String sourceFileName) {
        this.sourceFileName = sourceFileName;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getContentType() {
        return contentType;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setParsedCharCount(Integer parsedCharCount) {
        this.parsedCharCount = parsedCharCount;
    }

    public Integer getParsedCharCount() {
        return parsedCharCount;
    }

    public void setTruncated(Boolean truncated) {
        this.truncated = truncated;
    }

    public Boolean getTruncated() {
        return truncated;
    }

    public void setCandidateCreatedAt(LocalDateTime candidateCreatedAt) {
        this.candidateCreatedAt = candidateCreatedAt;
    }

    public LocalDateTime getCandidateCreatedAt() {
        return candidateCreatedAt;
    }

    public void setEmailsJson(String emailsJson) {
        this.emailsJson = emailsJson;
    }

    public String getEmailsJson() {
        return emailsJson;
    }

    public void setPhonesJson(String phonesJson) {
        this.phonesJson = phonesJson;
    }

    public String getPhonesJson() {
        return phonesJson;
    }

    public void setEducationsJson(String educationsJson) {
        this.educationsJson = educationsJson;
    }

    public String getEducationsJson() {
        return educationsJson;
    }

    public void setHonorsJson(String honorsJson) {
        this.honorsJson = honorsJson;
    }

    public String getHonorsJson() {
        return honorsJson;
    }

    public void setLinksJson(String linksJson) {
        this.linksJson = linksJson;
    }

    public String getLinksJson() {
        return linksJson;
    }
}
