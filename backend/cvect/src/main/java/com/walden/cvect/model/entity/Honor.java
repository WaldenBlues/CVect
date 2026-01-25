package com.walden.cvect.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "honors")
public class Honor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID candidateId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    protected Honor() {
    }

    public Honor(UUID candidateId, String content) {
        this.candidateId = candidateId;
        this.content = content;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Honor honor = (Honor) o;
        return Objects.equals(id, honor.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Honor{" +
                "id=" + id +
                ", candidateId=" + candidateId +
                ", content='" + content + '\'' +
                '}';
    }
}
