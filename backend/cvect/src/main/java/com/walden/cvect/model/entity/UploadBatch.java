package com.walden.cvect.model.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Check;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 简历上传批次
 */
@Entity
@Table(
        name = "upload_batches",
        indexes = @Index(name = "idx_upload_batches_jd_id", columnList = "jd_id")
)
@Check(constraints = "status in ('PROCESSING','DONE')")
public class UploadBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jd_id", foreignKey = @ForeignKey(name = "fk_upload_batches_jd"))
    private JobDescription jobDescription;

    @Column(name = "total_files")
    private Integer totalFiles;

    @Column(name = "processed_files")
    private Integer processedFiles;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private UploadBatchStatus status;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected UploadBatch() {
    }

    public UploadBatch(JobDescription jobDescription, int totalFiles) {
        this.jobDescription = jobDescription;
        this.totalFiles = totalFiles;
        this.processedFiles = 0;
        this.status = UploadBatchStatus.PROCESSING;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public JobDescription getJobDescription() {
        return jobDescription;
    }

    public Integer getTotalFiles() {
        return totalFiles;
    }

    public void setTotalFiles(Integer totalFiles) {
        this.totalFiles = totalFiles;
    }

    public Integer getProcessedFiles() {
        return processedFiles;
    }

    public void setProcessedFiles(Integer processedFiles) {
        this.processedFiles = processedFiles;
    }

    public UploadBatchStatus getStatus() {
        return status;
    }

    public void setStatus(UploadBatchStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UploadBatch that = (UploadBatch) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
