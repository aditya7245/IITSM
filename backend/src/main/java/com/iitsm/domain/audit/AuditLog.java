package com.iitsm.domain.audit;

import com.iitsm.domain.common.CreatedOnlyEntity;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.user.User;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * One line of the permanent history: who did what to which incident, and the status change if any.
 * Append-only: there are no setters, and the database refuses UPDATE and DELETE on this table.
 * A null user means the system itself did it.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog extends CreatedOnlyEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id")
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(name = "previous_state", length = 20)
    private String previousState;

    @Column(name = "new_state", length = 20)
    private String newState;

    @Column
    private String description;

    protected AuditLog() { }

    public AuditLog(Incident incident, User user, String action, String previousState, String newState,
                    String description, Instant createdAt) {
        this.incident = incident;
        this.user = user;
        this.action = action;
        this.previousState = previousState;
        this.newState = newState;
        this.description = description;
        initCreatedAt(createdAt);
    }

    public Incident getIncident() { return incident; }
    public User getUser() { return user; }
    public String getAction() { return action; }
    public String getPreviousState() { return previousState; }
    public String getNewState() { return newState; }
    public String getDescription() { return description; }
}
