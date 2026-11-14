package com.iitsm.domain.incident;

import com.iitsm.domain.catalog.Category;
import com.iitsm.domain.catalog.ITService;
import com.iitsm.domain.common.BaseEntity;
import com.iitsm.domain.incident.state.IncidentStateMachine;
import com.iitsm.domain.user.Technician;
import com.iitsm.domain.user.User;
import com.iitsm.exception.InvalidInputException;
import com.iitsm.exception.InvalidStateTransitionException;
import jakarta.persistence.*;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/**
 * One reported IT problem and everything we track about it.
 * <p>
 * Every change goes through a method on this class, and each method first checks the lifecycle rules
 * ({@link IncidentStateMachine}) and its own input, so an incident can never end up in an invalid state.
 * If a method throws, the incident is left unchanged. Times are passed in so tests can control them.
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

    /**
     * Optimistic lock. Two transactions that both loaded the same version cannot both save: the second one fails
     * instead of silently overwriting the first (for example the SLA monitor and a technician acting at once).
     */
    @Version
    private Long version;

    /** Statuses in which an admin may hand the work to another technician without changing the status. */
    private static final Set<IncidentStatus> HANDOVER = EnumSet.of(IncidentStatus.ACKNOWLEDGED,
            IncidentStatus.IN_PROGRESS, IncidentStatus.PENDING_INFO);

    /** Statuses in which the priority may still be changed: classified, but not yet resolved. */
    private static final Set<IncidentStatus> PRIORITY_CHANGEABLE = EnumSet.of(IncidentStatus.CLASSIFIED,
            IncidentStatus.ASSIGNED, IncidentStatus.ACKNOWLEDGED, IncidentStatus.IN_PROGRESS, IncidentStatus.PENDING_INFO);

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

    // ------------------------------------------------------------------ behaviour

    /** Records the priority and SLA deadlines worked out for a new incident. NEW to CLASSIFIED. */
    public void classify(Priority priority, int score, Instant responseDeadline, Instant resolutionDeadline) {
        requireMoveTo(IncidentStatus.CLASSIFIED);
        if (priority == null || responseDeadline == null || resolutionDeadline == null) {
            throw new InvalidInputException("Priority and both SLA deadlines are required");
        }
        this.priority = priority;
        this.priorityScore = score;
        this.responseDeadline = responseDeadline;
        this.resolutionDeadline = resolutionDeadline;
        this.status = IncidentStatus.CLASSIFIED;
    }

    /** Assigns a technician. CLASSIFIED to ASSIGNED; while already ASSIGNED it reassigns without changing the status. */
    public void assignTo(Technician technician) {
        if (technician == null) {
            throw new InvalidInputException("A technician is required");
        }
        if (status != IncidentStatus.ASSIGNED) {
            requireMoveTo(IncidentStatus.ASSIGNED);
            this.status = IncidentStatus.ASSIGNED;
        }
        this.assignee = technician;
    }

    /**
     * Hands work that has already started (ACKNOWLEDGED, IN_PROGRESS or PENDING_INFO) to another technician, for
     * example when the first one is away. The status and the SLA times stay as they are; the new technician
     * carries on from where the work is.
     */
    public void handOverTo(Technician technician) {
        if (technician == null) {
            throw new InvalidInputException("A technician is required");
        }
        if (!HANDOVER.contains(status)) {
            throw new InvalidStateTransitionException(status.name(), status.name());
        }
        this.assignee = technician;
    }

    /** True while the incident can be given to a technician, either by assignment or by hand-over. */
    public boolean canBeReassigned() {
        return status == IncidentStatus.CLASSIFIED || status == IncidentStatus.ASSIGNED || HANDOVER.contains(status);
    }

    /** The technician accepts the incident. This counts as the response for the SLA. ASSIGNED to ACKNOWLEDGED. */
    public void acknowledge(Instant now) {
        requireMoveTo(IncidentStatus.ACKNOWLEDGED);
        this.status = IncidentStatus.ACKNOWLEDGED;
        this.respondedAt = now;
    }

    /** Investigation begins. ACKNOWLEDGED to IN_PROGRESS. */
    public void startProgress() {
        requireFrom(IncidentStatus.ACKNOWLEDGED, IncidentStatus.IN_PROGRESS);
        this.status = IncidentStatus.IN_PROGRESS;
    }

    /** The technician asks the reporter for more information; the SLA clock pauses. IN_PROGRESS to PENDING_INFO. */
    public void requestInfo(Instant now) {
        requireMoveTo(IncidentStatus.PENDING_INFO);
        this.status = IncidentStatus.PENDING_INFO;
        this.slaPausedAt = now;
    }

    /**
     * The reporter answered; the SLA clock resumes. PENDING_INFO to IN_PROGRESS.
     * Both deadlines move later by the time spent waiting, and that time is added to the paused total.
     */
    public void receiveInfo(Instant now) {
        requireFrom(IncidentStatus.PENDING_INFO, IncidentStatus.IN_PROGRESS);
        long pausedSeconds = slaPausedAt == null ? 0 : Math.max(0, Duration.between(slaPausedAt, now).getSeconds());
        Duration paused = Duration.ofSeconds(pausedSeconds);
        if (responseDeadline != null) {
            this.responseDeadline = responseDeadline.plus(paused);
        }
        if (resolutionDeadline != null) {
            this.resolutionDeadline = resolutionDeadline.plus(paused);
        }
        this.slaPausedSeconds += pausedSeconds;
        this.slaPausedAt = null;
        this.status = IncidentStatus.IN_PROGRESS;
    }

    /** The technician describes the fix. IN_PROGRESS to RESOLVED. */
    public void resolve(String resolutionText, Instant now) {
        requireMoveTo(IncidentStatus.RESOLVED);
        if (resolutionText == null || resolutionText.isBlank()) {
            throw new InvalidInputException("Resolution details must not be blank");
        }
        this.resolution = resolutionText;
        this.resolvedAt = now;
        this.status = IncidentStatus.RESOLVED;
    }

    /** The reporter confirms the fix worked. RESOLVED to VERIFIED. */
    public void verify() {
        requireMoveTo(IncidentStatus.VERIFIED);
        this.status = IncidentStatus.VERIFIED;
    }

    /** The reporter says the fix did not work. RESOLVED back to IN_PROGRESS; the resolution is cleared. */
    public void reopen() {
        requireFrom(IncidentStatus.RESOLVED, IncidentStatus.IN_PROGRESS);
        this.resolution = null;
        this.resolvedAt = null;
        this.status = IncidentStatus.IN_PROGRESS;
    }

    /** Final close. VERIFIED to CLOSED. */
    public void close(Instant now) {
        requireMoveTo(IncidentStatus.CLOSED);
        this.closedAt = now;
        this.status = IncidentStatus.CLOSED;
    }

    /** Withdrawn before work started. NEW, CLASSIFIED or ASSIGNED to CANCELLED. */
    public void cancel() {
        requireMoveTo(IncidentStatus.CANCELLED);
        this.status = IncidentStatus.CANCELLED;
    }

    /** Marks this incident as another report of the same problem as {@code original}. */
    public void markDuplicateOf(Incident original) {
        requireMoveTo(IncidentStatus.DUPLICATE);
        if (original == null || original == this || (getId() != null && getId().equals(original.getId()))) {
            throw new InvalidInputException("An incident must be a duplicate of a different incident");
        }
        this.duplicateOf = original;
        this.status = IncidentStatus.DUPLICATE;
    }

    /** Replaces the priority and deadlines, for example when an admin overrides it. Not allowed once resolved. */
    public void changePriority(Priority newPriority, int score, Instant responseDeadline, Instant resolutionDeadline) {
        if (!PRIORITY_CHANGEABLE.contains(status)) {
            throw new InvalidInputException("Priority cannot be changed while the incident is " + status);
        }
        if (newPriority == null || responseDeadline == null || resolutionDeadline == null) {
            throw new InvalidInputException("Priority and both SLA deadlines are required");
        }
        this.priority = newPriority;
        this.priorityScore = score;
        this.responseDeadline = responseDeadline;
        this.resolutionDeadline = resolutionDeadline;
    }

    /**
     * Raises the SLA status if {@code newStatus} is more severe than the current one; never lowers it.
     *
     * @return true if the status changed
     */
    public boolean raiseSlaStatus(SlaStatus newStatus) {
        if (newStatus != null && newStatus.isMoreSevereThan(slaStatus)) {
            this.slaStatus = newStatus;
            return true;
        }
        return false;
    }

    /**
     * Sets the SLA status to a freshly evaluated value, which may be lower than before. Only used after the
     * deadlines themselves were recomputed (an admin changed the priority); the monitor only ever raises it.
     */
    public void reevaluateSlaStatus(SlaStatus evaluated) {
        if (evaluated == null) {
            throw new InvalidInputException("An SLA status is required");
        }
        this.slaStatus = evaluated;
    }

    public void incrementEscalationLevel() {
        this.escalationLevel++;
    }

    // ------------------------------------------------------------------ helpers

    private void requireMoveTo(IncidentStatus target) {
        IncidentStateMachine.requireTransition(status, target);
    }

    /**
     * For methods whose target status can also be reached from other statuses (for example IN_PROGRESS):
     * the incident must be in exactly {@code expected}.
     */
    private void requireFrom(IncidentStatus expected, IncidentStatus target) {
        if (status != expected) {
            throw new InvalidStateTransitionException(status.name(), target.name());
        }
        requireMoveTo(target);
    }
}
