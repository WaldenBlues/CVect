package com.walden.cvect.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Experience that = (Experience) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Experience{" +
                "id=" + id +
                ", candidateId=" + candidateId +
                ", company='" + company + '\'' +
                ", position='" + position + '\'' +
                ", startDate='" + startDate + '\'' +
                ", endDate='" + endDate + '\'' +
                '}';
    }
}
