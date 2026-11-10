package com.iitsm.service;

import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentStatus;
import com.iitsm.domain.incident.Priority;
import com.iitsm.domain.sla.Deadlines;
import com.iitsm.domain.sla.SlaEvaluator;
import com.iitsm.domain.sla.SlaPolicy;
import com.iitsm.domain.user.Technician;
import com.iitsm.domain.user.User;
import com.iitsm.config.SlaProperties;
import com.iitsm.exception.IncidentNotFoundException;
import com.iitsm.exception.InvalidInputException;
import com.iitsm.exception.InvalidStateTransitionException;
import com.iitsm.exception.UnauthorizedOperationException;
import com.iitsm.observer.IncidentEvent;
import com.iitsm.observer.IncidentEventPublisher;
import com.iitsm.observer.IncidentEventType;
import com.iitsm.repository.IncidentRepository;
import com.iitsm.security.IncidentAccessPolicy;
import com.iitsm.strategy.PriorityCalculator;
import com.iitsm.strategy.PriorityResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The use cases of the incident lifecycle. Each public method is one transaction: either everything
 * happens (the change, its audit record) or nothing does. Reporting an incident is {@link #create};
 * the actions people take afterwards (acknowledge, resolve, cancel, ...) follow it. Every action works the same way:
 * load the incident (404 if missing), check who is asking, apply the change through the incident's own method
 * (which checks the lifecycle rules, 409 if not allowed), save, and announce the event.
 * Comments and feedback are added in 4.7 and 4.8.
 */
@Service
public class IncidentService {

    static final int DEFAULT_BUMP_USERS = 100;
    static final int MAX_TEXT_IN_EVENT = 200;

    private final IncidentFactory factory;
    private final IncidentRepository incidents;
    private final PriorityCalculator priorityCalculator;
    private final SlaPolicyService slaPolicies;
    private final SlaEvaluator slaEvaluator;
    private final SlaProperties slaProperties;
    private final ConfigService config;
    private final DuplicateDetectionService duplicateDetection;
    private final AssignmentService assignment;
    private final IncidentEventPublisher events;
    private final IncidentAccessPolicy access;
    private final Clock clock;

    public IncidentService(IncidentFactory factory, IncidentRepository incidents, PriorityCalculator priorityCalculator,
                           SlaPolicyService slaPolicies, SlaEvaluator slaEvaluator, SlaProperties slaProperties,
                           ConfigService config, DuplicateDetectionService duplicateDetection,
                           AssignmentService assignment, IncidentEventPublisher events,
                           IncidentAccessPolicy access, Clock clock) {
        this.factory = factory;
        this.incidents = incidents;
        this.priorityCalculator = priorityCalculator;
        this.slaPolicies = slaPolicies;
        this.slaEvaluator = slaEvaluator;
        this.slaProperties = slaProperties;
        this.config = config;
        this.duplicateDetection = duplicateDetection;
        this.assignment = assignment;
        this.events = events;
        this.access = access;
        this.clock = clock;
    }

    /**
     * Reports a new incident and runs the whole chain in one transaction:
     * create and save it (CREATED), work out its priority and SLA deadlines (CLASSIFIED), look for possible
     * duplicates, then try to assign a technician (ASSIGNED). If nobody is eligible the incident stays
     * CLASSIFIED and the admins are told (ASSIGNMENT_FAILED).
     * <p>
     * Must not be called from a read-only transaction, because taking an incident number writes to a sequence.
     *
     * @param actor the user reporting the problem
     * @throws UnauthorizedOperationException if the user may not report (for example a deactivated account)
     * @throws com.iitsm.exception.InvalidInputException      if the report is invalid (see {@link IncidentFactory})
     * @throws com.iitsm.exception.ServiceNotFoundException   if the service is unknown or inactive
     * @throws com.iitsm.exception.MissingSlaConfigurationException if there is no SLA policy for the priority
     */
    @Transactional
    public CreateIncidentResult create(CreateIncidentCommand command, User actor) {
        Objects.requireNonNull(actor, "actor");
        if (!access.canReport(actor)) {
            throw new UnauthorizedOperationException();
        }

        Incident incident = incidents.save(factory.create(command, actor));
        publish(IncidentEventType.CREATED, incident, actor, null,
                "Incident " + incident.getIncidentNumber() + " reported: " + incident.getTitle());

        classify(incident);

        List<DuplicateCandidate> possibleDuplicates = duplicateDetection.findCandidates(incident);

        assign(incident);

        return new CreateIncidentResult(incident, possibleDuplicates);
    }

    // ------------------------------------------------------------------ lifecycle actions

    /** The assignee accepts the incident. ASSIGNED to ACKNOWLEDGED; this counts as the SLA response. */
    @Transactional
    public Incident acknowledge(Long id, User actor) {
        Incident incident = load(id);
        access.requireAssignee(actor, incident);
        IncidentStatus previous = incident.getStatus();
        incident.acknowledge(clock.instant());
        return saveAndPublish(IncidentEventType.ACKNOWLEDGED, incident, actor, previous,
                "Acknowledged by " + actor.getFullName());
    }

    /** The assignee begins investigating. ACKNOWLEDGED to IN_PROGRESS. */
    @Transactional
    public Incident startProgress(Long id, User actor) {
        Incident incident = load(id);
        access.requireAssignee(actor, incident);
        IncidentStatus previous = incident.getStatus();
        incident.startProgress();
        return saveAndPublish(IncidentEventType.INVESTIGATION_STARTED, incident, actor, previous,
                "Investigation started by " + actor.getFullName());
    }

    /** The assignee describes the fix. IN_PROGRESS to RESOLVED; the incident stops counting toward their workload. */
    @Transactional
    public Incident resolve(Long id, String resolution, User actor) {
        Incident incident = load(id);
        access.requireAssignee(actor, incident);
        IncidentStatus previous = incident.getStatus();
        incident.resolve(resolution, clock.instant());
        return saveAndPublish(IncidentEventType.RESOLVED, incident, actor, previous,
                "Resolved by " + actor.getFullName() + ": " + shorten(resolution));
    }

    /** The reporter confirms the fix worked. RESOLVED to VERIFIED. */
    @Transactional
    public Incident verify(Long id, User actor) {
        Incident incident = load(id);
        access.requireReporter(actor, incident);
        IncidentStatus previous = incident.getStatus();
        incident.verify();
        return saveAndPublish(IncidentEventType.VERIFIED, incident, actor, previous,
                "Fix verified by " + actor.getFullName());
    }

    /** The reporter says the fix did not work and gives a reason. RESOLVED back to IN_PROGRESS. */
    @Transactional
    public Incident reopen(Long id, String reason, User actor) {
        Incident incident = load(id);
        access.requireReporter(actor, incident);
        if (reason == null || reason.isBlank()) {
            throw new InvalidInputException("A reason for reopening is required");
        }
        IncidentStatus previous = incident.getStatus();
        incident.reopen();
        return saveAndPublish(IncidentEventType.REOPENED, incident, actor, previous,
                "Reopened by " + actor.getFullName() + ": " + shorten(reason));
    }

    /** The reporter or an admin withdraws the incident before work started. NEW, CLASSIFIED or ASSIGNED to CANCELLED. */
    @Transactional
    public Incident cancel(Long id, User actor) {
        Incident incident = load(id);
        access.requireReporterOrAdmin(actor, incident);
        IncidentStatus previous = incident.getStatus();
        incident.cancel();
        return saveAndPublish(IncidentEventType.CANCELLED, incident, actor, previous,
                "Cancelled by " + actor.getFullName());
    }

    /** An admin closes a verified incident without waiting for feedback. VERIFIED to CLOSED. */
    @Transactional
    public Incident close(Long id, User actor) {
        Incident incident = load(id);
        access.requireAdmin(actor);
        IncidentStatus previous = incident.getStatus();
        incident.close(clock.instant());
        return saveAndPublish(IncidentEventType.CLOSED, incident, actor, previous,
                "Closed by " + actor.getFullName());
    }

    /** An admin marks the incident as another report of {@code originalId}. Allowed from NEW up to ACKNOWLEDGED. */
    @Transactional
    public Incident markDuplicate(Long id, Long originalId, User actor) {
        Incident incident = load(id);
        access.requireAdmin(actor);
        if (originalId == null) {
            throw new InvalidInputException("The original incident is required");
        }
        Incident original = load(originalId);
        IncidentStatus previous = incident.getStatus();
        incident.markDuplicateOf(original);
        return saveAndPublish(IncidentEventType.MARKED_DUPLICATE, incident, actor, previous,
                "Marked as a duplicate of " + original.getIncidentNumber());
    }

    /**
     * An admin hands the incident to a technician of their choice. Only while CLASSIFIED (nobody assigned yet,
     * for example after ASSIGNMENT_FAILED) or ASSIGNED (not yet acknowledged); later reassignment is out of scope.
     *
     * @throws InvalidStateTransitionException                     if the incident is in any other status
     * @throws InvalidInputException                                if the technician already has this incident
     * @throws com.iitsm.exception.TechnicianUnavailableException   see {@link AssignmentService#assignManually}
     * @throws com.iitsm.exception.WorkloadExceededException        see {@link AssignmentService#assignManually}
     */
    @Transactional
    public Incident reassign(Long id, Long technicianId, User actor) {
        Incident incident = load(id);
        access.requireAdmin(actor);
        IncidentStatus previous = incident.getStatus();
        if (previous != IncidentStatus.CLASSIFIED && previous != IncidentStatus.ASSIGNED) {
            throw new InvalidStateTransitionException(previous.name(), IncidentStatus.ASSIGNED.name());
        }
        if (incident.getAssignee() != null && incident.getAssignee().getId().equals(technicianId)) {
            throw new InvalidInputException("The incident is already assigned to " + incident.getAssignee().getFullName());
        }
        Technician technician = assignment.assignManually(incident, technicianId);
        incident.assignTo(technician);
        return saveAndPublish(IncidentEventType.ASSIGNED, incident, actor, previous,
                (previous == IncidentStatus.ASSIGNED ? "Reassigned to " : "Assigned to ") + technician.getFullName()
                        + " by " + actor.getFullName());
    }

    /**
     * An admin overrides the priority. The SLA deadlines are recomputed from the incident's creation time for the
     * new priority, keeping the time already spent paused. The stored score stays as it was calculated.
     * Not allowed once the incident is resolved.
     */
    @Transactional
    public Incident changePriority(Long id, Priority newPriority, User actor) {
        Incident incident = load(id);
        access.requireAdmin(actor);
        if (newPriority == null) {
            throw new InvalidInputException("A priority is required");
        }
        Priority oldPriority = incident.getPriority();
        SlaPolicy policy = slaPolicies.findPolicy(newPriority, incident.getService());
        Deadlines deadlines = slaEvaluator.computeDeadlines(incident.getCreatedAt(), policy.getResponseMinutes(),
                policy.getResolutionMinutes(), slaProperties.timeScale(), incident.getSlaPausedSeconds());
        int score = incident.getPriorityScore() == null ? 0 : incident.getPriorityScore();

        incident.changePriority(newPriority, score, deadlines.responseDeadline(), deadlines.resolutionDeadline());
        return saveAndPublish(IncidentEventType.PRIORITY_CHANGED, incident, actor, incident.getStatus(),
                "Priority changed from " + oldPriority + " to " + newPriority + " by " + actor.getFullName());
    }

    // ------------------------------------------------------------------ steps of create

    /** Priority from impact, urgency, service criticality and number of users; SLA deadlines counted from creation. */
    private void classify(Incident incident) {
        PriorityResult priority = priorityCalculator.calculate(incident.getImpact(), incident.getUrgency(),
                incident.getService().getBusinessCriticality(), incident.getAffectedUsers(),
                config.getInt(ConfigKeys.PRIORITY_BUMP_USERS, DEFAULT_BUMP_USERS));
        SlaPolicy policy = slaPolicies.findPolicy(priority.priority(), incident.getService());
        Deadlines deadlines = slaEvaluator.computeDeadlines(incident.getCreatedAt(), policy.getResponseMinutes(),
                policy.getResolutionMinutes(), slaProperties.timeScale(), 0);

        incident.classify(priority.priority(), priority.score(), deadlines.responseDeadline(), deadlines.resolutionDeadline());
        incidents.save(incident);
        publish(IncidentEventType.CLASSIFIED, incident, null, IncidentStatus.NEW,
                "Priority " + priority.priority() + " (score " + priority.score() + ")");
    }

    private void assign(Incident incident) {
        Optional<Technician> chosen = assignment.autoAssign(incident);
        if (chosen.isPresent()) {
            incident.assignTo(chosen.get());
            incidents.save(incident);
            publish(IncidentEventType.ASSIGNED, incident, null, IncidentStatus.CLASSIFIED,
                    "Assigned to " + chosen.get().getFullName());
        } else {
            publish(IncidentEventType.ASSIGNMENT_FAILED, incident, null, IncidentStatus.CLASSIFIED,
                    "No eligible technician found; an administrator must assign this incident");
        }
    }

    private Incident load(Long id) {
        return incidents.findById(id).orElseThrow(() -> new IncidentNotFoundException(String.valueOf(id)));
    }

    private Incident saveAndPublish(IncidentEventType type, Incident incident, User actor,
                                    IncidentStatus previous, String description) {
        incidents.save(incident);
        publish(type, incident, actor, previous, description);
        return incident;
    }

    /** Keeps free text short enough for a notification line (they are limited to 500 characters). */
    static String shorten(String text) {
        String oneLine = text.strip().replaceAll("\\s+", " ");
        return oneLine.length() <= MAX_TEXT_IN_EVENT ? oneLine : oneLine.substring(0, MAX_TEXT_IN_EVENT - 3) + "...";
    }

    /** Announces an event; {@code previous} is null only for creation. The new status is the incident's current one. */
    private void publish(IncidentEventType type, Incident incident, User actor, IncidentStatus previous, String description) {
        events.publish(IncidentEvent.of(type, incident, actor, previous, incident.getStatus(), description, clock.instant()));
    }
}
