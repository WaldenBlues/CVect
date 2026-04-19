package com.walden.cvect.model.entity;

import com.walden.cvect.model.TenantConstants;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * JD 组（岗位描述）
 */
@Entity
@Table(name = "job_descriptions", indexes = @Index(name = "idx_job_descriptions_tenant_id", columnList = "tenant_id"))
public class JobDescription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Convert(converter = FloatArrayTextConverter.class)
    @Column(name = "embedding", columnDefinition = "TEXT")
    private float[] embedding;

    @Column(name = "embedding_updated_at")
    private LocalDateTime embeddingUpdatedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    protected JobDescription() {
    }

    public JobDescription(String title, String content) {
        this(TenantConstants.DEFAULT_TENANT_ID, title, content);
    }

    public JobDescription(UUID tenantId, String title, String content) {
        this.tenantId = tenantId == null ? TenantConstants.DEFAULT_TENANT_ID : tenantId;
        this.title = title;
        this.content = content;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.tenantId == null) {
            this.tenantId = TenantConstants.DEFAULT_TENANT_ID;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }

    public LocalDateTime getEmbeddingUpdatedAt() {
        return embeddingUpdatedAt;
    }

    public void setEmbeddingUpdatedAt(LocalDateTime embeddingUpdatedAt) {
        this.embeddingUpdatedAt = embeddingUpdatedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JobDescription that = (JobDescription) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
