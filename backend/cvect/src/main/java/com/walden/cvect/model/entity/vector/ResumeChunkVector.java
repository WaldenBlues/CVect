package com.walden.cvect.model.entity.vector;

import com.walden.cvect.model.ChunkType;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 向量存储实体 - 存储 EXPERIENCE/SKILL 的向量化版本
 */
@Entity
@Table(name = "resume_chunks", indexes = {
    @Index(name = "idx_candidate_id", columnList = "candidateId"),
    @Index(name = "idx_chunk_type", columnList = "chunkType")
})
public class ResumeChunkVector {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID candidateId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChunkType chunkType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // H2兼容：使用 TEXT 存储（开发/测试环境）
    // 生产环境通过 Hibernate 物理命名策略转换为 vector(768)
    @Column(columnDefinition = "TEXT")
    private float[] embedding;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    protected ResumeChunkVector() {
    }

    public ResumeChunkVector(UUID candidateId, ChunkType chunkType, String content, float[] embedding) {
        this.candidateId = candidateId;
        this.chunkType = chunkType;
        this.content = content;
        this.embedding = embedding;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
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

    public float[] getEmbedding() {
        return embedding;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
