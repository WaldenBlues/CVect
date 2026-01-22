package com.walden.cvect.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 教育背景实体
 */
@Entity
@Table(name = "educations")
public class Education {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID candidateId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String school;

    @Column(length = 100)
    private String major;

    @Column(length = 20)
    private String degree;

    @Column(name = "graduation_year", length = 10)
    private String graduationYear;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    protected Education() {
    }

    public Education(UUID candidateId, String school, String major, String degree) {
        this.candidateId = candidateId;
        this.school = school;
        this.major = major;
        this.degree = degree;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
