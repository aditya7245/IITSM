package com.iitsm.service;

import com.iitsm.domain.audit.AuditLog;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentStatus;
import com.iitsm.domain.incident.Priority;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The escalation chain against the real database, moving the test clock. The payment outage is a P1 assigned to
 * tech1: response deadline 5 minutes after creation, and one level timeout is 5 minutes (the defaults, with a time
 * scale of 1). Rolls back after each test.
 */
@Transactional
class EscalationServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired EscalationService escalation;
    @Autowired IncidentService incidentService;
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

    private Incident report(short impact, short urgency, int affectedUsers) {
        CreateIncidentCommand command = new CreateIncidentCommand("Payment application outage",
                "Users cannot complete payment transactions",
                categories.findByName("Application").orElseThrow().getId(),
                services.findByName("Payment Application").orElseThrow().getId(),
                impact, urgency, affectedUsers);
        return incidentService.create(command, user("emp1")).incident();
    }

    private Incident reportP1() {
        Incident incident = report((short) 3, (short) 3, 250);
        assertEquals(Priority.P1, incident.getPriority());
        assertEquals(IncidentStatus.ASSIGNED, incident.getStatus());
        return incident;
    }

    private int storedLevel(Incident incident) {
        incidents.flush();
        em.clear();
        return incidents.findById(incident.getId()).orElseThrow().getEscalationLevel();
    }

    private List<AuditLog> escalationLines(Incident incident) {
        return auditService.history(incident.getId()).stream().filter(line -> line.getAction().equals("ESCALATED")).toList();
    }

    @Test
    void levelOneAtTheResponseDeadlineLevelTwoOneTimeoutLaterLevelThreeOneMoreLaterThenNothing() {
        Incident incident = reportP1();

        clock.advance(Duration.ofMinutes(4).plusSeconds(59));         // one second before the response deadline
        assertEquals(0, escalation.runOnce());
        assertEquals(0, storedLevel(incident));

        clock.advance(Duration.ofSeconds(1));                         // exactly the response deadline
        assertEquals(1, escalation.runOnce());
        assertEquals(1, storedLevel(incident));
        assertEquals(0, escalation.runOnce());                        // nothing new on a repeat run

        clock.advance(Duration.ofMinutes(5));                         // one timeout later
        assertEquals(1, escalation.runOnce());
        assertEquals(2, storedLevel(incident));

        clock.advance(Duration.ofMinutes(5));                         // one more
        assertEquals(1, escalation.runOnce());
        assertEquals(3, storedLevel(incident));

        clock.advance(Duration.ofDays(1));
        assertEquals(0, escalation.runOnce());
        assertEquals(3, storedLevel(incident));
        assertEquals(3, escalationLines(incident).size());
    }

    @Test
    void eachEscalationIsRecordedAsDoneByTheSystemAndNamesTheLevelWithoutReassigning() {
        Incident incident = reportP1();
        clock.advance(Duration.ofMinutes(5));
        escalation.runOnce();
        clock.advance(Duration.ofMinutes(5));
        escalation.runOnce();

        List<AuditLog> lines = escalationLines(incident);

        assertEquals(2, lines.size());
        assertTrue(lines.get(0).getDescription().contains("level 1 of 3"), lines.get(0).getDescription());
        assertTrue(lines.get(1).getDescription().contains("level 2 of 3"), lines.get(1).getDescription());
        assertNull(lines.get(0).getUser());
        assertEquals("ASSIGNED", lines.get(0).getPreviousState());
        assertEquals("ASSIGNED", lines.get(0).getNewState());
        Incident same = incidents.findById(incident.getId()).orElseThrow();
        assertEquals("tech1@iitsm.local", same.getAssignee().getEmail());     // still with the original technician
        assertEquals(IncidentStatus.ASSIGNED, same.getStatus());
    }

    @Test
    void nothingHappensAfterTheIncidentIsAcknowledged() {
        Incident incident = reportP1();
        clock.advance(Duration.ofMinutes(5));
        escalation.runOnce();                                          // level 1 at the deadline
        incidentService.acknowledge(incident.getId(), user("tech1"));

        clock.advance(Duration.ofHours(1));

        assertEquals(0, escalation.runOnce());
        assertEquals(1, storedLevel(incident));
        assertEquals(1, escalationLines(incident).size());
    }

    @Test
    void anIncidentAcknowledgedInTimeIsNeverEscalated() {
        Incident incident = reportP1();
        clock.advance(Duration.ofMinutes(2));
        incidentService.acknowledge(incident.getId(), user("tech1"));

        clock.advance(Duration.ofHours(1));

        assertEquals(0, escalation.runOnce());
        assertEquals(0, storedLevel(incident));
        assertTrue(escalationLines(incident).isEmpty());
    }

    @Test
    void onlyP1IncidentsAreEscalated() {
        Incident minor = report((short) 1, (short) 1, 1);
        assertNotEquals(Priority.P1, minor.getPriority());

        clock.advance(Duration.ofHours(5));

        assertEquals(0, escalation.runOnce());
        assertEquals(0, storedLevel(minor));
    }
}
