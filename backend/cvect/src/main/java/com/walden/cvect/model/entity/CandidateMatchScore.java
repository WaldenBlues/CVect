package com.walden.cvect.model.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "candidate_match_scores",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_candidate_match_scores_candidate_jd",
                columnNames = {"candidate_id", "jd_id"}),
        indexes = {
                @Index(name = "idx_candidate_match_scores_candidate_id", columnList = "candidate_id"),
                @Index(name = "idx_candidate_match_scores_jd_id", columnList = "jd_id")
        })
public class CandidateMatchScore {

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
            foreignKey = @ForeignKey(name = "fk_candidate_match_scores_candidate"))
    private Candidate candidate;

    @Column(name = "jd_id", nullable = false)
    private UUID jobDescriptionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "jd_id",
            nullable = false,
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_candidate_match_scores_jd"))
    private JobDescription jobDescription;

    @Column(name = "overall_score", nullable = false)
    private float overallScore;

    @Column(name = "experience_score", nullable = false)
    private float experienceScore;

    @Column(name = "skill_score", nullable = false)
    private float skillScore;

    @Column(name = "scored_at", nullable = false)
    private LocalDateTime scoredAt;

    protected CandidateMatchScore() {
    }

    public CandidateMatchScore(
            UUID candidateId,
            UUID jobDescriptionId,
            float overallScore,
            float experienceScore,
            float skillScore,
            LocalDateTime scoredAt) {
        this.candidateId = candidateId;
        this.jobDescriptionId = jobDescriptionId;
        this.overallScore = overallScore;
        this.experienceScore = experienceScore;
        this.skillScore = skillScore;
        this.scoredAt = scoredAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCandidateId() {
        return candidateId;
    }

    public UUID getJobDescriptionId() {
        return jobDescriptionId;
    }

    public float getOverallScore() {
        return overallScore;
    }

    public void setOverallScore(float overallScore) {
        this.overallScore = overallScore;
    }

    public float getExperienceScore() {
        return experienceScore;
    }

    public void setExperienceScore(float experienceScore) {
        this.experienceScore = experienceScore;
    }

    public float getSkillScore() {
        return skillScore;
    }

    public void setSkillScore(float skillScore) {
        this.skillScore = skillScore;
    }

    public LocalDateTime getScoredAt() {
        return scoredAt;
    }

    public void setScoredAt(LocalDateTime scoredAt) {
        this.scoredAt = scoredAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CandidateMatchScore that = (CandidateMatchScore) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
