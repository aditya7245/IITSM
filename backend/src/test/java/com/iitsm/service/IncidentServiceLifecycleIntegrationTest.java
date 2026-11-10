package com.iitsm.service;

import com.iitsm.domain.audit.AuditLog;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentStatus;
import com.iitsm.domain.incident.Priority;
import com.iitsm.domain.user.User;
import com.iitsm.exception.InvalidStateTransitionException;
import com.iitsm.exception.UnauthorizedOperationException;
import com.iitsm.repository.CategoryRepository;
import com.iitsm.repository.ITServiceRepository;
import com.iitsm.repository.IncidentRepository;
import com.iitsm.repository.UserRepository;
import com.iitsm.support.AbstractIntegrationTest;
import com.iitsm.support.MutableClock;
import com.iitsm.support.TestClockConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The lifecycle actions against the real database and seeded data, following demo workflow 1:
 * emp1 reports a payment outage, tech1 gets it and works it, emp1 verifies, an admin closes it.
 * Rolls back after each test. Assumes the default time scale of 1.
 */
@Transactional
class IncidentServiceLifecycleIntegrationTest extends AbstractIntegrationTest {

    @Autowired IncidentService incidentService;
    @Autowired AuditService auditService;
    @Autowired IncidentRepository incidents;
    @Autowired UserRepository users;
    @Autowired ITServiceRepository services;
    @Autowired CategoryRepository categories;
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    @BeforeEach
    void setUp() {
        clock.reset();
    }

    private User user(String name) {
        return users.findByEmailIgnoreCase(name + "@iitsm.local").orElseThrow();
    }

    /** Reports the payment outage as emp1; tech1 is picked automatically. */
    private Incident report() {
        CreateIncidentCommand command = new CreateIncidentCommand("Payment application outage",
                "Users cannot complete payment transactions",
                categories.findByName("Application").orElseThrow().getId(),
                services.findByName("Payment Application").orElseThrow().getId(),
                (short) 3, (short) 3, 250);
        return incidentService.create(command, user("emp1")).incident();
    }

    private long workload(String technician) {
        return incidents.countByAssigneeIdAndStatusIn(user(technician).getId(), IncidentStatus.workloadStatuses());
    }

    private Incident reload(Incident incident) {
        incidents.flush();
        em.clear();
        return incidents.findById(incident.getId()).orElseThrow();
    }

    private List<String> auditActions(Incident incident) {
        return auditService.history(incident.getId()).stream().map(AuditLog::getAction).toList();
    }

    @Test
    void theWholeLifeOfTheIncidentFromReportToClose() {
        Incident incident = report();
        Long id = incident.getId();
        assertEquals(1, workload("tech1"));

        clock.advance(Duration.ofMinutes(2));
        incidentService.acknowledge(id, user("tech1"));
        incidentService.startProgress(id, user("tech1"));
        incidentService.resolve(id, "Restarted the payment gateway service", user("tech1"));
        assertEquals(0, workload("tech1"));              // a resolved incident no longer counts toward the workload

        incidentService.verify(id, user("emp1"));
        incidentService.close(id, user("admin"));

        Incident closed = reload(incident);
        assertEquals(IncidentStatus.CLOSED, closed.getStatus());
        assertEquals("Restarted the payment gateway service", closed.getResolution());
        assertEquals(TestClockConfig.START.plus(Duration.ofMinutes(2)), closed.getRespondedAt());
        assertNotNull(closed.getResolvedAt());
        assertNotNull(closed.getClosedAt());
        assertEquals(List.of("CREATED", "CLASSIFIED", "ASSIGNED", "ACKNOWLEDGED", "INVESTIGATION_STARTED",
                "RESOLVED", "VERIFIED", "CLOSED"), auditActions(closed));
    }

    @Test
    void reopeningPutsTheIncidentBackOnTheTechniciansWorkload() {
        Incident incident = report();
        Long id = incident.getId();
        incidentService.acknowledge(id, user("tech1"));
        incidentService.startProgress(id, user("tech1"));
        incidentService.resolve(id, "Restarted the gateway", user("tech1"));
        assertEquals(0, workload("tech1"));

        incidentService.reopen(id, "Payments still fail for some users", user("emp1"));

        Incident reopened = reload(incident);
        assertEquals(IncidentStatus.IN_PROGRESS, reopened.getStatus());
        assertNull(reopened.getResolution());
        assertEquals(1, workload("tech1"));
        List<AuditLog> history = auditService.history(id);
        AuditLog last = history.get(history.size() - 1);
        assertEquals("REOPENED", last.getAction());
        assertEquals("RESOLVED", last.getPreviousState());
        assertEquals("IN_PROGRESS", last.getNewState());
        assertTrue(last.getDescription().contains("Payments still fail for some users"));
    }

    @Test
    void theReporterCanCancelAnAssignedIncident() {
        Incident incident = report();

        incidentService.cancel(incident.getId(), user("emp1"));

        assertEquals(IncidentStatus.CANCELLED, reload(incident).getStatus());
        assertEquals(0, workload("tech1"));
        assertTrue(auditActions(incident).contains("CANCELLED"));
    }

    @Test
    void anAdminAssignsAnIncidentThatCouldNotBeAssignedAutomatically() {
        jdbc.update("update users set available = false where role in ('TECHNICIAN', 'TEAM_LEAD')");
        em.clear();
        Incident incident = report();
        assertEquals(IncidentStatus.CLASSIFIED, incident.getStatus());
        jdbc.update("update users set available = true where email = 'tech2@iitsm.local'");
        em.clear();

        incidentService.reassign(incident.getId(), user("tech2").getId(), user("admin"));

        Incident assigned = reload(incident);
        assertEquals(IncidentStatus.ASSIGNED, assigned.getStatus());
        assertEquals("tech2@iitsm.local", assigned.getAssignee().getEmail());
        assertEquals(List.of("CREATED", "CLASSIFIED", "ASSIGNMENT_FAILED", "ASSIGNED"), auditActions(assigned));
    }

    @Test
    void anAdminMovesAnAssignedIncidentToAnotherTechnician() {
        Incident incident = report();
        assertEquals("tech1@iitsm.local", incident.getAssignee().getEmail());

        incidentService.reassign(incident.getId(), user("tech2").getId(), user("admin"));

        assertEquals("tech2@iitsm.local", reload(incident).getAssignee().getEmail());
        assertEquals(0, workload("tech1"));
        assertEquals(1, workload("tech2"));
    }

    @Test
    void aDuplicateIsStoredWithAReferenceToTheOriginal() {
        Incident original = report();
        Incident copy = report();

        incidentService.markDuplicate(copy.getId(), original.getId(), user("admin"));

        Incident reloaded = reload(copy);
        assertEquals(IncidentStatus.DUPLICATE, reloaded.getStatus());
        assertEquals(original.getId(), reloaded.getDuplicateOf().getId());
        assertTrue(auditActions(reloaded).contains("MARKED_DUPLICATE"));
        assertEquals(1, workload("tech1"));              // only the original still counts
    }

    @Test
    void changingThePriorityStoresNewDeadlinesFromTheSeededPolicy() {
        Incident incident = report();                    // P1: 5 and 30 minutes

        incidentService.changePriority(incident.getId(), Priority.P3, user("admin"));   // P3: 60 and 480 minutes

        Incident changed = reload(incident);
        assertEquals(Priority.P3, changed.getPriority());
        assertEquals(45, changed.getPriorityScore().intValue());
        assertEquals(TestClockConfig.START.plus(Duration.ofMinutes(60)), changed.getResponseDeadline());
        assertEquals(TestClockConfig.START.plus(Duration.ofMinutes(480)), changed.getResolutionDeadline());
        List<AuditLog> history = auditService.history(incident.getId());
        assertEquals("PRIORITY_CHANGED", history.get(history.size() - 1).getAction());
    }

    @Test
    void theWrongTechnicianCannotWorkOnSomeoneElsesIncident() {
        Incident incident = report();     // belongs to tech1

        UnauthorizedOperationException refused = assertThrows(UnauthorizedOperationException.class,
                () -> incidentService.acknowledge(incident.getId(), user("tech2")));

        assertEquals(HttpStatus.FORBIDDEN, refused.getStatus());
        assertEquals(IncidentStatus.ASSIGNED, reload(incident).getStatus());
    }

    @Test
    void anActionNotAllowedInTheCurrentStatusIsRefusedAndNothingIsRecorded() {
        Incident incident = report();     // ASSIGNED
        int auditBefore = auditService.history(incident.getId()).size();

        InvalidStateTransitionException refused = assertThrows(InvalidStateTransitionException.class,
                () -> incidentService.close(incident.getId(), user("admin")));

        assertEquals(HttpStatus.CONFLICT, refused.getStatus());   // the 409 the REST layer will return
        assertEquals(IncidentStatus.ASSIGNED, reload(incident).getStatus());
        assertEquals(auditBefore, auditService.history(incident.getId()).size());
    }
}
