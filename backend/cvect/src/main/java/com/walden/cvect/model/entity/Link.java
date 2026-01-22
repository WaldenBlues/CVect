package com.walden.cvect.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 个人链接实体（GitHub、博客等）
 */
@Entity
@Table(name = "links")
public class Link {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID candidateId;

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
}
