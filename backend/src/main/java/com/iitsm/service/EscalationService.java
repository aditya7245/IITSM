package com.iitsm.service;

import com.iitsm.config.EscalationProperties;
import com.iitsm.config.SlaProperties;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentStatus;
import com.iitsm.domain.incident.Priority;
import com.iitsm.domain.user.Administrator;
import com.iitsm.domain.user.ITManager;
import com.iitsm.domain.user.Team;
import com.iitsm.domain.user.Technician;
import com.iitsm.domain.user.User;
import com.iitsm.observer.IncidentEvent;
import com.iitsm.observer.IncidentEventPublisher;
import com.iitsm.observer.IncidentEventType;
import com.iitsm.repository.IncidentRepository;
import com.iitsm.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Raises the alarm when a critical (P1) incident sits assigned but unacknowledged. Each time one more
 * "level timeout" has passed beyond the response deadline, the next person up the chain is alerted:
 * <ol>
 *   <li>the team lead of the assignee's team (the administrators if there is no usable team lead),</li>
 *   <li>the administrators,</li>
 *   <li>the IT managers.</li>
 * </ol>
 * Escalation only alerts and records the level on the incident; it never reassigns it. At most one level is
 * raised per incident per run, and it stops as soon as the incident is acknowledged. Each incident is handled in
 * its own transaction and try/catch, like in {@link SlaMonitorService}. {@link EscalationScheduler} calls
 * {@link #runOnce()} on a timer.
 */
@Service
public class EscalationService {

    static final int MAX_LEVEL = 3;

    private static final Logger log = LoggerFactory.getLogger(EscalationService.class);

    private final IncidentRepository incidents;
    private final UserRepository users;
    private final SlaProperties slaProperties;
    private final EscalationProperties escalationProperties;
    private final IncidentEventPublisher events;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public EscalationService(IncidentRepository incidents, UserRepository users, SlaProperties slaProperties,
                             EscalationProperties escalationProperties, IncidentEventPublisher events,
                             TransactionTemplate transactions, Clock clock) {
        this.incidents = incidents;
        this.users = users;
        this.slaProperties = slaProperties;
        this.escalationProperties = escalationProperties;
        this.events = events;
        this.transactions = transactions;
        this.clock = clock;
    }

    /**
     * Escalates every P1 incident that is still ASSIGNED (not acknowledged), is below level 3 and has waited long
     * enough for its next level, using one reading of the clock for the whole run.
     *
     * @return how many incidents were escalated by one level in this run
     */
    public int runOnce() {
        Instant now = clock.instant();
        int escalated = 0;
        for (Incident listed : incidents.findByStatusNotIn(IncidentStatus.terminalStatuses())) {
            if (!isCandidate(listed)) {
                continue;
            }
            Long id = listed.getId();
            try {
                if (Boolean.TRUE.equals(transactions.execute(status -> escalate(id, now)))) {
                    escalated++;
                }
            } catch (RuntimeException e) {
                log.warn("Escalation failed for incident {}: {}", id, e.toString());
            }
        }
        return escalated;
    }

    private static boolean isCandidate(Incident incident) {
        return incident.getPriority() == Priority.P1
                && incident.getStatus() == IncidentStatus.ASSIGNED
                && incident.getEscalationLevel() < MAX_LEVEL
                && incident.getResponseDeadline() != null;
    }

    /** Escalates one incident inside its own transaction; true if it moved up a level. */
    private boolean escalate(Long id, Instant now) {
        Incident incident = incidents.findById(id).orElse(null);
        if (incident == null || !isCandidate(incident)) {
            return false;                       // changed (for example acknowledged) since the list was read
        }
        int level = incident.getEscalationLevel() + 1;
        if (now.isBefore(whenLevelIsDue(incident, level))) {
            return false;
        }

        Targets targets = targetsFor(incident, level);
        incident.incrementEscalationLevel();
        incidents.save(incident);
        String description = "Not acknowledged in time: escalated to level " + level + " of " + MAX_LEVEL
                + ", alerting " + targets.label();
        events.publish(new IncidentEvent(IncidentEventType.ESCALATED, incident, null, incident.getStatus(),
                incident.getStatus(), description, now, targets.users().stream().map(User::getId).toList()));
        return true;
    }

    /** Level 1 is due at the response deadline, each further level one level timeout later (shortened by the time scale). */
    private Instant whenLevelIsDue(Incident incident, int level) {
        double minutes = (level - 1) * (double) escalationProperties.levelTimeoutMinutes();
        return incident.getResponseDeadline().plus(Duration.ofMillis(Math.round(minutes * 60_000.0 / slaProperties.timeScale())));
    }

    private Targets targetsFor(Incident incident, int level) {
        return switch (level) {
            case 1 -> {
                List<User> lead = teamLeadOf(incident);
                yield lead.isEmpty()
                        ? new Targets(active(users.findAllByExactType(Administrator.class)),
                                "the administrators (there is no team lead to alert)")
                        : new Targets(lead, "the team lead");
            }
            case 2 -> new Targets(active(users.findAllByExactType(Administrator.class)), "the administrators");
            default -> new Targets(active(users.findAllByExactType(ITManager.class)), "the IT managers");
        };
    }

    /** The assignee's team lead, unless there is none, the lead is deactivated, or the lead is the assignee. */
    private static List<User> teamLeadOf(Incident incident) {
        Technician assignee = incident.getAssignee();
        Team team = assignee == null ? null : assignee.getTeam();
        User lead = team == null ? null : team.getLead();
        if (lead == null || !lead.isActive() || lead.getId().equals(assignee.getId())) {
            return List.of();
        }
        return List.of(lead);
    }

    private static List<User> active(List<? extends User> candidates) {
        return candidates.stream().filter(User::isActive).map(user -> (User) user).toList();
    }

    private record Targets(List<User> users, String label) { }
}
