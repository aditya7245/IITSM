package com.iitsm.service;

import com.iitsm.domain.audit.AuditLog;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentStatus;
import com.iitsm.domain.incident.SlaStatus;
import com.iitsm.domain.user.User;
import com.iitsm.repository.CategoryRepository;
import com.iitsm.repository.ITServiceRepository;
import com.iitsm.repository.IncidentRepository;
import com.iitsm.repository.UserRepository;
import com.iitsm.support.AbstractIntegrationTest;
import com.iitsm.support.MutableClock;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The SLA monitor against the real database, moving the test clock. Every incident is the payment outage
 * (P1: respond within 5 minutes, resolve within 30, warning at 80% of the allowed time) assigned to tech1.
 * Assumes the default time scale of 1. Rolls back after each test.
 */
@Transactional
class SlaMonitorServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired SlaMonitorService monitor;
    @Autowired IncidentService incidentService;
    @Autowired IncidentCommentService commentService;
    @Autowired AuditService auditService;
    @Autowired IncidentRepository incidents;
    @Autowired UserRepository users;
    @Autowired ITServiceRepository services;
    @Autowired CategoryRepository categories;
    @Autowired MutableClock clock;
    @Autowired EntityManager em;

    @BeforeEach
    void setUp() {
        clock.reset();
    }

    private User user(String name) {
        return users.findByEmailIgnoreCase(name + "@iitsm.local").orElseThrow();
    }

    private Incident report() {
        CreateIncidentCommand command = new CreateIncidentCommand("Payment application outage",
                "Users cannot complete payment transactions",
                categories.findByName("Application").orElseThrow().getId(),
                services.findByName("Payment Application").orElseThrow().getId(),
                (short) 3, (short) 3, 250);
        return incidentService.create(command, user("emp1")).incident();
    }

    private SlaStatus storedSlaStatus(Incident incident) {
        incidents.flush();
        em.clear();
        return incidents.findById(incident.getId()).orElseThrow().getSlaStatus();
    }

    private long eventsOf(Incident incident, String action) {
        return auditService.history(incident.getId()).stream().map(AuditLog::getAction).filter(action::equals).count();
    }

    @Test
    void onTrackThenAtRiskAtEightyPercentThenViolatedAfterTheDeadlineWithExactlyOneEventEach() {
        Incident incident = report();                      // not yet acknowledged: the 5 minute response deadline is running

        assertEquals(0, monitor.runOnce());                // just created
        assertEquals(SlaStatus.ON_TRACK, storedSlaStatus(incident));
        assertEquals(0, eventsOf(incident, "SLA_WARNING") + eventsOf(incident, "SLA_VIOLATED"));

        clock.advance(Duration.ofMinutes(4));              // 80% of 5 minutes
        assertEquals(1, monitor.runOnce());
        assertEquals(0, monitor.runOnce());                // nothing new on a repeat run
        assertEquals(SlaStatus.AT_RISK, storedSlaStatus(incident));
        assertEquals(1, eventsOf(incident, "SLA_WARNING"));
        assertEquals(0, eventsOf(incident, "SLA_VIOLATED"));

        clock.advance(Duration.ofMinutes(2));              // 6 minutes: past the response deadline
        assertEquals(1, monitor.runOnce());
        assertEquals(0, monitor.runOnce());
        assertEquals(SlaStatus.VIOLATED, storedSlaStatus(incident));
        assertEquals(1, eventsOf(incident, "SLA_WARNING"));
        assertEquals(1, eventsOf(incident, "SLA_VIOLATED"));
    }

    @Test
    void theEventsAreRecordedAsDoneByTheSystemAndKeepTheIncidentsStatus() {
        Incident incident = report();
        clock.advance(Duration.ofMinutes(4));

        monitor.runOnce();

        AuditLog warning = auditService.history(incident.getId()).stream()
                .filter(line -> line.getAction().equals("SLA_WARNING")).findFirst().orElseThrow();
        assertNull(warning.getUser());
        assertEquals("ASSIGNED", warning.getPreviousState());
        assertEquals("ASSIGNED", warning.getNewState());
        assertEquals(IncidentStatus.ASSIGNED, incidents.findById(incident.getId()).orElseThrow().getStatus());
    }

    @Test
    void onceAcknowledgedTheResolutionDeadlineIsTheOneBeingWatched() {
        Incident incident = report();
        clock.advance(Duration.ofMinutes(1));
        incidentService.acknowledge(incident.getId(), user("tech1"));
        incidentService.startProgress(incident.getId(), user("tech1"));

        clock.advance(Duration.ofMinutes(9));              // 10 minutes in: the response deadline passed but was met
        assertEquals(0, monitor.runOnce());
        assertEquals(SlaStatus.ON_TRACK, storedSlaStatus(incident));

        clock.advance(Duration.ofMinutes(14));             // 24 minutes: 80% of 30
        assertEquals(1, monitor.runOnce());
        assertEquals(SlaStatus.AT_RISK, storedSlaStatus(incident));

        clock.advance(Duration.ofMinutes(7));              // 31 minutes: past the resolution deadline
        assertEquals(1, monitor.runOnce());
        assertEquals(SlaStatus.VIOLATED, storedSlaStatus(incident));
        assertEquals(1, eventsOf(incident, "SLA_WARNING"));
        assertEquals(1, eventsOf(incident, "SLA_VIOLATED"));
    }

    @Test
    void waitingForTheReporterDoesNotUseUpTheTime() {
        Incident incident = report();
        clock.advance(Duration.ofMinutes(1));
        incidentService.acknowledge(incident.getId(), user("tech1"));
        incidentService.startProgress(incident.getId(), user("tech1"));
        commentService.requestInfo(incident.getId(), "Which users are affected?", user("tech1"));

        clock.advance(Duration.ofHours(2));                // the reporter is slow
        assertEquals(0, monitor.runOnce());
        assertEquals(SlaStatus.ON_TRACK, storedSlaStatus(incident));

        commentService.respondToInfo(incident.getId(), "Everyone in Finance", user("emp1"));
        assertEquals(0, monitor.runOnce());                // the deadlines moved later by the two hours
        assertEquals(SlaStatus.ON_TRACK, storedSlaStatus(incident));
        assertEquals(0, eventsOf(incident, "SLA_WARNING") + eventsOf(incident, "SLA_VIOLATED"));
    }

    @Test
    void finishedIncidentsAreNotChecked() {
        Incident incident = report();
        incidentService.cancel(incident.getId(), user("emp1"));

        clock.advance(Duration.ofHours(5));

        assertEquals(0, monitor.runOnce());
        assertEquals(SlaStatus.ON_TRACK, storedSlaStatus(incident));
    }
}
