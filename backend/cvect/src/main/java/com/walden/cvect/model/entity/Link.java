package com.walden.cvect.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "links")
public class Link {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "candidate_id",
            nullable = false,
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_links_candidate"))
    private Candidate candidate;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String url;

    @Column(length = 50)
    private String platform;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    protected Link() {
    }

    public Link(UUID candidateId, String url) {
        this.candidateId = candidateId;
        this.url = url;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        // 根据URL自动识别平台
        if (platform == null || platform.isBlank()) {
            if (url.contains("github.com")) {
                platform = "GITHUB";
            } else if (url.contains("gitee.com")) {
                platform = "GITEE";
            } else if (url.contains("blog")) {
                platform = "BLOG";
            } else {
                platform = "OTHER";
            }
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getCandidateId() {
        return candidateId;
    }

    public String getUrl() {
        return url;
    }

    public String getPlatform() {
        return platform;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Link link = (Link) o;
        return Objects.equals(id, link.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Link{" +
                "id=" + id +
                ", candidateId=" + candidateId +
                ", url='" + url + '\'' +
                ", platform='" + platform + '\'' +
                '}';
    }
}
