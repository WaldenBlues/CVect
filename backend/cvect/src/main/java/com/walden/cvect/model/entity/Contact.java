package com.walden.cvect.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "contacts")
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID candidateId;

    @Column(length = 20, nullable = false)
    private String type;

    @Column(name = "contact_value", nullable = false, columnDefinition = "TEXT")
    private String value;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    protected Contact() {
    }

    public Contact(UUID candidateId, String type, String value) {
        this.candidateId = candidateId;
        this.type = type;
        this.value = value;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Contact contact = (Contact) o;
        return Objects.equals(id, contact.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Contact{" +
                "id=" + id +
                ", candidateId=" + candidateId +
                ", type='" + type + '\'' +
                ", value='" + value + '\'' +
                '}';
    }
}
