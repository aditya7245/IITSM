package com.iitsm.domain.incident;

import com.iitsm.domain.catalog.Category;
import com.iitsm.domain.catalog.ITService;
import com.iitsm.domain.common.BaseEntity;
import com.iitsm.domain.user.Technician;
import com.iitsm.domain.user.User;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * One reported IT problem and everything we track about it.
 * This class holds the data only; the behaviour (acknowledge, resolve, pause the SLA clock, ...) is added in item 2.4.
 */
@Entity
@Table(name = "incidents")
public class Incident extends BaseEntity {

    @Column(name = "incident_number", nullable = false, unique = true, length = 20)
    private String incidentNumber;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private ITService service;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private Technician assignee;

    @Column(nullable = false)
    private short impact;

    @Column(nullable = false)
    private short urgency;

    @Column(name = "affected_users", nullable = false)
    private int affectedUsers;

    @Column(name = "priority_score")
    private Integer priorityScore;

    @Enumerated(EnumType.STRING)
    @Column(length = 2)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentStatus status = IncidentStatus.NEW;

    @Enumerated(EnumType.STRING)
    @Column(name = "sla_status", nullable = false, length = 10)
    private SlaStatus slaStatus = SlaStatus.ON_TRACK;

    @Column(name = "response_deadline")
    private Instant responseDeadline;

    @Column(name = "resolution_deadline")
    private Instant resolutionDeadline;

    /** Set while the SLA clock is paused (waiting for the reporter); null otherwise. */
    @Column(name = "sla_paused_at")
    private Instant slaPausedAt;

    /** Total seconds the SLA clock has been paused so far. */
    @Column(name = "sla_paused_seconds", nullable = false)
    private long slaPausedSeconds;

    @Column(name = "escalation_level", nullable = false)
    private short escalationLevel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "duplicate_of_id")
    private Incident duplicateOf;

    @Column
    private String resolution;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected Incident() { }  // JPA

    public Incident(String incidentNumber, String title, String description, User reporter, ITService service,
                    Category category, short impact, short urgency, int affectedUsers, Instant createdAt) {
        this.incidentNumber = incidentNumber;
        this.title = title;
        this.description = description;
        this.reporter = reporter;
        this.service = service;
        this.category = category;
        this.impact = impact;
        this.urgency = urgency;
        this.affectedUsers = affectedUsers;
        initCreatedAt(createdAt);
    }

    public String getIncidentNumber() { return incidentNumber; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public User getReporter() { return reporter; }
    public Category getCategory() { return category; }
    public ITService getService() { return service; }
    public Technician getAssignee() { return assignee; }
    public short getImpact() { return impact; }
    public short getUrgency() { return urgency; }
    public int getAffectedUsers() { return affectedUsers; }
    public Integer getPriorityScore() { return priorityScore; }
    public Priority getPriority() { return priority; }
    public IncidentStatus getStatus() { return status; }
    public SlaStatus getSlaStatus() { return slaStatus; }
    public Instant getResponseDeadline() { return responseDeadline; }
    public Instant getResolutionDeadline() { return resolutionDeadline; }
    public Instant getSlaPausedAt() { return slaPausedAt; }
    public long getSlaPausedSeconds() { return slaPausedSeconds; }
    public short getEscalationLevel() { return escalationLevel; }
    public Incident getDuplicateOf() { return duplicateOf; }
    public String getResolution() { return resolution; }
    public Instant getRespondedAt() { return respondedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Instant getClosedAt() { return closedAt; }
}
