package com.iitsm.domain.common;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Shared identity and timestamps for tables that have BOTH created_at and updated_at
 * (users, teams, incidents). Tables with only created_at extend CreatedOnlyEntity instead.
 */
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Lets a subclass fix the creation time before the first save (e.g. an incident's SLA start). */
    protected void initCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BaseEntity other) || id == null) return false;
        return id.equals(other.id) && org.hibernate.Hibernate.getClass(this) == org.hibernate.Hibernate.getClass(other);
    }

    @Override
    public int hashCode() {
        return org.hibernate.Hibernate.getClass(this).hashCode();
    }
}
