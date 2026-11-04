package com.iitsm.domain.common;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Identity plus created_at only, for append-style tables that have no updated_at column
 * (incident_comments, audit_logs, notifications, feedback).
 */
@MappedSuperclass
public abstract class CreatedOnlyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected void initCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CreatedOnlyEntity other) || id == null) return false;
        return id.equals(other.id) && org.hibernate.Hibernate.getClass(this) == org.hibernate.Hibernate.getClass(other);
    }

    @Override
    public int hashCode() {
        return org.hibernate.Hibernate.getClass(this).hashCode();
    }
}
