package com.walden.cvect.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 工作/项目经历实体
 */
@Entity
@Table(name = "experiences")
public class Experience {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID candidateId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String company;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String position;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_date", length = 20)
    private String startDate;

    @Column(name = "end_date", length = 20)
    private String endDate;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    protected Experience() {
    }

    public Experience(UUID candidateId, String company, String position, String description) {
        this.candidateId = candidateId;
        this.company = company;
        this.position = position;
        this.description = description;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
