package com.walden.cvect.model.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Check;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 批次内单文件结果
 */
@Entity
@Table(
        name = "upload_items",
        uniqueConstraints = @UniqueConstraint(name = "uk_upload_items_queue_job_key", columnNames = "queue_job_key")
)
@Check(constraints = "status in ('PENDING','QUEUED','PROCESSING','RETRYING','DONE','SUCCEEDED','DUPLICATE','FAILED')")
public class UploadItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", foreignKey = @ForeignKey(name = "fk_upload_items_batch"))
    private UploadBatch batch;

    @Column(name = "file_name", columnDefinition = "TEXT")
    private String fileName;

    @Column(name = "candidate_id")
    private UUID candidateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private UploadItemStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "storage_path", columnDefinition = "TEXT")
    private String storagePath;

    @Column(name = "attempt")
    private Integer attempt;

    @Column(name = "queue_job_key", length = 128)
    private String queueJobKey;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected UploadItem() {
    }

    public UploadItem(UploadBatch batch, String fileName) {
        this.batch = batch;
        this.fileName = fileName;
        this.status = UploadItemStatus.PROCESSING;
        this.attempt = 0;
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

    public UploadBatch getBatch() {
        return batch;
    }

    public String getFileName() {
        return fileName;
    }

    public UUID getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(UUID candidateId) {
        this.candidateId = candidateId;
    }

    public UploadItemStatus getStatus() {
        return status;
    }

    public void setStatus(UploadItemStatus status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Integer getAttempt() {
        return attempt;
    }

    public void setAttempt(Integer attempt) {
        this.attempt = attempt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public String getQueueJobKey() {
        return queueJobKey;
    }

    public void setQueueJobKey(String queueJobKey) {
        this.queueJobKey = queueJobKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UploadItem that = (UploadItem) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
