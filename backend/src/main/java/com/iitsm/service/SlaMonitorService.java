package com.iitsm.service;

import com.iitsm.config.SlaProperties;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentStatus;
import com.iitsm.domain.incident.SlaStatus;
import com.iitsm.domain.sla.SlaEvaluator;
import com.iitsm.observer.IncidentEvent;
import com.iitsm.observer.IncidentEventPublisher;
import com.iitsm.observer.IncidentEventType;
import com.iitsm.repository.IncidentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Checks the SLA deadlines of every open incident. When an incident has become more serious than the status
 * stored on it (ON_TRACK, then AT_RISK once the warning threshold of the allowed time has passed, then VIOLATED
 * once a deadline has passed) the new status is saved and SLA_WARNING or SLA_VIOLATED is announced, once per change.
 * An incident never goes back to a milder status, so repeated runs announce nothing new.
 * <p>
 * Every incident is handled in its own transaction and its own try/catch, so one problem incident cannot
 * undo or stop the check of the others. {@link SlaMonitorScheduler} calls {@link #runOnce()} on a timer.
 */
@Service
public class SlaMonitorService {

    private static final Logger log = LoggerFactory.getLogger(SlaMonitorService.class);

    private final IncidentRepository incidents;
    private final SlaEvaluator evaluator;
    private final SlaProperties properties;
    private final IncidentEventPublisher events;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public SlaMonitorService(IncidentRepository incidents, SlaEvaluator evaluator, SlaProperties properties,
                             IncidentEventPublisher events, TransactionTemplate transactions, Clock clock) {
        this.incidents = incidents;
        this.evaluator = evaluator;
        this.properties = properties;
        this.events = events;
        this.transactions = transactions;
        this.clock = clock;
    }

    /**
     * Checks every open incident that has a priority, using one reading of the clock for the whole run.
     *
     * @return how many incidents became more serious in this run
     */
    public int runOnce() {
        Instant now = clock.instant();
        List<Incident> open = incidents.findByStatusNotIn(IncidentStatus.terminalStatuses());
        int raised = 0;
        for (Incident listed : open) {
            if (listed.getPriority() == null) {
                continue;                       // not classified yet, so no SLA applies
            }
            Long id = listed.getId();
            try {
                if (Boolean.TRUE.equals(transactions.execute(status -> check(id, now)))) {
                    raised++;
                }
            } catch (RuntimeException e) {
                log.warn("SLA check failed for incident {}: {}", id, e.toString());
            }
        }
        return raised;
    }

    /** Checks one incident inside its own transaction; true if its SLA status was raised. */
    private boolean check(Long id, Instant now) {
        Incident incident = incidents.findById(id).orElse(null);
        if (incident == null || incident.getPriority() == null || !incident.getStatus().isOpen()) {
            return false;                       // changed or removed since the list was read
        }
        SlaStatus current = evaluator.evaluate(incident.getCreatedAt(), incident.getResponseDeadline(),
                incident.getResolutionDeadline(), incident.getRespondedAt(), incident.getResolvedAt(),
                incident.getSlaPausedAt(), incident.getSlaPausedSeconds(), now, properties.warningThreshold());
        if (!incident.raiseSlaStatus(current)) {
            return false;
        }
        incidents.save(incident);
        IncidentEventType type = current == SlaStatus.VIOLATED ? IncidentEventType.SLA_VIOLATED : IncidentEventType.SLA_WARNING;
        String description = current == SlaStatus.VIOLATED
                ? "SLA violated: a deadline has passed"
                : "SLA at risk: more than " + Math.round(properties.warningThreshold() * 100) + "% of the allowed time has passed";
        events.publish(IncidentEvent.of(type, incident, null, incident.getStatus(), incident.getStatus(), description, now));
        return true;
    }
}
