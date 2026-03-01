package com.walden.cvect.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "educations")
public class Education {

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
            foreignKey = @ForeignKey(name = "fk_educations_candidate"))
    private Candidate candidate;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String school;

    @Column(columnDefinition = "TEXT")
    private String major;

    @Column(columnDefinition = "TEXT")
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

    public UUID getId() {
        return id;
    }

    public UUID getCandidateId() {
        return candidateId;
    }

    public String getSchool() {
        return school;
    }

    public String getMajor() {
        return major;
    }

    public String getDegree() {
        return degree;
    }

    public String getGraduationYear() {
        return graduationYear;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Education education = (Education) o;
        return Objects.equals(id, education.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Education{" +
                "id=" + id +
                ", candidateId=" + candidateId +
                ", school='" + school + '\'' +
                ", major='" + major + '\'' +
                ", degree='" + degree + '\'' +
                ", graduationYear='" + graduationYear + '\'' +
                '}';
    }
}
