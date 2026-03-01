package com.walden.cvect.model.entity.vector;

import com.walden.cvect.model.ChunkType;
import jakarta.persistence.*;
import org.hibernate.annotations.Check;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "vector_ingest_tasks",
        indexes = {
                @Index(name = "idx_vector_ingest_status_updated", columnList = "status,updated_at"),
                @Index(name = "idx_vector_ingest_candidate", columnList = "candidate_id")
        }
)
@Check(constraints = "status in ('PENDING','PROCESSING','DONE','FAILED')")
public class VectorIngestTask {

    @Id
    private UUID id;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "chunk_type", nullable = false, length = 32)
    private ChunkType chunkType;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private VectorIngestTaskStatus status;

    @Column(name = "attempt", nullable = false)
    private Integer attempt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected VectorIngestTask() {
    }

    public VectorIngestTask(UUID candidateId, ChunkType chunkType, String content) {
        this.id = UUID.randomUUID();
        this.candidateId = candidateId;
        this.chunkType = chunkType;
        this.content = content;
        this.status = VectorIngestTaskStatus.PENDING;
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

    public UUID getCandidateId() {
        return candidateId;
    }

    public ChunkType getChunkType() {
        return chunkType;
    }

    public String getContent() {
        return content;
    }

    public VectorIngestTaskStatus getStatus() {
        return status;
    }

    public Integer getAttempt() {
        return attempt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setStatus(VectorIngestTaskStatus status) {
        this.status = status;
    }

    public void setAttempt(Integer attempt) {
        this.attempt = attempt;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        VectorIngestTask that = (VectorIngestTask) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
