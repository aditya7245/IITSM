package com.iitsm.service;

import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentStatus;
import com.iitsm.domain.sla.Deadlines;
import com.iitsm.domain.sla.SlaEvaluator;
import com.iitsm.domain.sla.SlaPolicy;
import com.iitsm.domain.user.Technician;
import com.iitsm.domain.user.User;
import com.iitsm.config.SlaProperties;
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
 * happens (the change, its audit record) or nothing does. This item adds reporting an incident;
 * the other lifecycle actions are added in 4.6 to 4.8.
 */
@Service
public class IncidentService {

    static final int DEFAULT_BUMP_USERS = 100;

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

    /** Announces an event; {@code previous} is null only for creation. The new status is the incident's current one. */
    private void publish(IncidentEventType type, Incident incident, User actor, IncidentStatus previous, String description) {
        events.publish(IncidentEvent.of(type, incident, actor, previous, incident.getStatus(), description, clock.instant()));
    }
}
