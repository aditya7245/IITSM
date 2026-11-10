package com.iitsm.service;

import com.iitsm.domain.audit.AuditLog;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentStatus;
import com.iitsm.domain.incident.Priority;
import com.iitsm.domain.user.User;
import com.iitsm.exception.InvalidInputException;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Demo workflow 1 against the real database and the seeded data: the employee emp1 reports a payment outage.
 * Rolls back after each test, so notifications (which are sent only after a commit) are not part of these tests.
 * Assumes the default time scale of 1 (a P1 has 5 minutes to respond and 30 to resolve).
 */
@Transactional
class IncidentServiceCreateIntegrationTest extends AbstractIntegrationTest {

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

    private User emp1() {
        return users.findByEmailIgnoreCase("emp1@iitsm.local").orElseThrow();
    }

    private CreateIncidentCommand paymentOutage(String title, String description) {
        return new CreateIncidentCommand(title, description,
                categories.findByName("Application").orElseThrow().getId(),
                services.findByName("Payment Application").orElseThrow().getId(),
                (short) 3, (short) 3, 250);
    }

    private CreateIncidentCommand paymentOutage() {
        return paymentOutage("Payment application outage", "Users cannot complete payment transactions");
    }

    private List<String> auditActions(Incident incident) {
        return auditService.history(incident.getId()).stream().map(AuditLog::getAction).toList();
    }

    @Test
    void paymentOutageGetsP1ItsSlaDeadlinesAndTheSkilledTechnician() {
        CreateIncidentResult result = incidentService.create(paymentOutage(), emp1());
        Incident incident = result.incident();

        assertNotNull(incident.getId());
        assertTrue(incident.getIncidentNumber().matches("INC\\d+"), incident.getIncidentNumber());
        assertEquals(IncidentStatus.ASSIGNED, incident.getStatus());
        assertEquals(Priority.P1, incident.getPriority());
        assertEquals(45, incident.getPriorityScore().intValue());
        assertEquals(TestClockConfig.START, incident.getCreatedAt());
        assertEquals(TestClockConfig.START.plus(Duration.ofMinutes(5)), incident.getResponseDeadline());
        assertEquals(TestClockConfig.START.plus(Duration.ofMinutes(30)), incident.getResolutionDeadline());
        // Application Support skill: tech1 has 5, tech3 has 2, and the team lead is left out of automatic assignment
        assertEquals("tech1@iitsm.local", incident.getAssignee().getEmail());
        assertEquals("emp1@iitsm.local", incident.getReporter().getEmail());
        assertTrue(result.possibleDuplicates().isEmpty());
    }

    @Test
    void theIncidentIsReallyStoredWithItsFinalState() {
        Incident created = incidentService.create(paymentOutage(), emp1()).incident();
        incidents.flush();
        em.clear();

        Incident reloaded = incidents.findById(created.getId()).orElseThrow();

        assertEquals(IncidentStatus.ASSIGNED, reloaded.getStatus());
        assertEquals(Priority.P1, reloaded.getPriority());
        assertEquals("tech1@iitsm.local", reloaded.getAssignee().getEmail());
        assertEquals(created.getResolutionDeadline(), reloaded.getResolutionDeadline());
    }

    @Test
    void auditHistoryHasCreatedClassifiedAndAssigned() {
        Incident incident = incidentService.create(paymentOutage(), emp1()).incident();

        List<AuditLog> history = auditService.history(incident.getId());

        assertEquals(List.of("CREATED", "CLASSIFIED", "ASSIGNED"), history.stream().map(AuditLog::getAction).toList());
        assertNull(history.get(0).getPreviousState());
        assertEquals("NEW", history.get(0).getNewState());
        assertEquals("NEW", history.get(1).getPreviousState());
        assertEquals("CLASSIFIED", history.get(1).getNewState());
        assertEquals("CLASSIFIED", history.get(2).getPreviousState());
        assertEquals("ASSIGNED", history.get(2).getNewState());
    }

    @Test
    void whenNoTechnicianIsEligibleTheIncidentStaysClassifiedAndAssignmentFailsInTheAudit() {
        jdbc.update("update users set available = false where role in ('TECHNICIAN', 'TEAM_LEAD')");
        em.clear();

        Incident incident = incidentService.create(paymentOutage(), emp1()).incident();

        assertEquals(IncidentStatus.CLASSIFIED, incident.getStatus());
        assertNull(incident.getAssignee());
        assertEquals(Priority.P1, incident.getPriority());   // still prioritised and given deadlines
        assertEquals(List.of("CREATED", "CLASSIFIED", "ASSIGNMENT_FAILED"), auditActions(incident));
    }

    @Test
    void aSimilarOpenIncidentOnTheSameServiceIsReportedAsPossibleDuplicate() {
        Incident first = incidentService.create(paymentOutage(), emp1()).incident();
        assertTrue(incidentService.create(paymentOutage("Unrelated", "Printer on floor three is jammed"), emp1())
                .possibleDuplicates().isEmpty());

        CreateIncidentResult second = incidentService.create(
                paymentOutage("Payment application down", "Users cannot complete payment transactions"), emp1());

        assertEquals(1, second.possibleDuplicates().size());
        assertEquals(first.getIncidentNumber(), second.possibleDuplicates().get(0).number());
        assertEquals(IncidentStatus.ASSIGNED, second.incident().getStatus());   // created normally despite the warning
    }

    @Test
    void anInvalidReportCreatesNothing() {
        long incidentsBefore = incidents.count();
        long auditBefore = jdbc.queryForObject("select count(*) from audit_logs", Long.class);

        assertThrows(InvalidInputException.class, () -> incidentService.create(paymentOutage("  ", "Some description"), emp1()));

        assertEquals(incidentsBefore, incidents.count());
        assertEquals(auditBefore, jdbc.queryForObject("select count(*) from audit_logs", Long.class).longValue());
    }
}
