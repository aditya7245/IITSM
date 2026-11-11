package com.iitsm.service;

import com.iitsm.domain.audit.AuditLog;
import com.iitsm.domain.incident.Feedback;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentStatus;
import com.iitsm.domain.user.User;
import com.iitsm.exception.InvalidInputException;
import com.iitsm.exception.InvalidStateTransitionException;
import com.iitsm.exception.UnauthorizedOperationException;
import com.iitsm.repository.CategoryRepository;
import com.iitsm.repository.FeedbackRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The end of demo workflow 1 against the real database: the reporter verified the fix, rates it, and the incident
 * closes by itself. Rolls back after each test.
 */
@Transactional
class FeedbackServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired FeedbackService feedbackService;
    @Autowired IncidentService incidentService;
    @Autowired AuditService auditService;
    @Autowired IncidentRepository incidents;
    @Autowired FeedbackRepository feedbacks;
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

    /** Reports the payment outage as emp1 and takes it, with tech1, as far as RESOLVED. */
    private Incident resolvedIncident() {
        CreateIncidentCommand command = new CreateIncidentCommand("Payment application outage",
                "Users cannot complete payment transactions",
                categories.findByName("Application").orElseThrow().getId(),
                services.findByName("Payment Application").orElseThrow().getId(),
                (short) 3, (short) 3, 250);
        Incident incident = incidentService.create(command, user("emp1")).incident();
        incidentService.acknowledge(incident.getId(), user("tech1"));
        incidentService.startProgress(incident.getId(), user("tech1"));
        incidentService.resolve(incident.getId(), "Restarted the payment gateway", user("tech1"));
        return incident;
    }

    private Incident verifiedIncident() {
        Incident incident = resolvedIncident();
        incidentService.verify(incident.getId(), user("emp1"));
        return incident;
    }

    private Incident reload(Incident incident) {
        incidents.flush();
        em.clear();
        return incidents.findById(incident.getId()).orElseThrow();
    }

    @Test
    void feedbackIsStoredAndTheIncidentClosesByItself() {
        Incident incident = verifiedIncident();
        clock.advance(Duration.ofMinutes(20));

        Feedback feedback = feedbackService.submitFeedback(incident.getId(), 5, "Fast and friendly, thanks", user("emp1"));

        assertNotNull(feedback.getId());
        Feedback stored = feedbacks.findByIncidentId(incident.getId()).orElseThrow();
        assertEquals(5, stored.getRating());
        assertEquals("Fast and friendly, thanks", stored.getComments());
        Incident closed = reload(incident);
        assertEquals(IncidentStatus.CLOSED, closed.getStatus());
        assertEquals(TestClockConfig.START.plus(Duration.ofMinutes(20)), closed.getClosedAt());
    }

    @Test
    void theAuditHistoryEndsWithVerifiedThenClosedBySystem() {
        Incident incident = verifiedIncident();

        feedbackService.submitFeedback(incident.getId(), 4, null, user("emp1"));

        List<AuditLog> history = auditService.history(incident.getId());
        assertEquals(List.of("CREATED", "CLASSIFIED", "ASSIGNED", "ACKNOWLEDGED", "INVESTIGATION_STARTED",
                "RESOLVED", "VERIFIED", "CLOSED"), history.stream().map(AuditLog::getAction).toList());
        AuditLog closedLine = history.get(history.size() - 1);
        assertEquals("VERIFIED", closedLine.getPreviousState());
        assertEquals("CLOSED", closedLine.getNewState());
        assertNull(closedLine.getUser());                       // done by the system, not by a person
    }

    @Test
    void aSecondSubmissionIsRefusedAndTheFirstFeedbackStays() {
        Incident incident = verifiedIncident();
        feedbackService.submitFeedback(incident.getId(), 5, "Great", user("emp1"));

        InvalidInputException refused = assertThrows(InvalidInputException.class,
                () -> feedbackService.submitFeedback(incident.getId(), 1, "Changed my mind", user("emp1")));

        assertEquals(HttpStatus.BAD_REQUEST, refused.getStatus());
        assertEquals(5, feedbacks.findByIncidentId(incident.getId()).orElseThrow().getRating());
    }

    @Test
    void feedbackBeforeTheFixIsVerifiedIsRefused() {
        Incident incident = resolvedIncident();                 // RESOLVED, not yet VERIFIED

        InvalidStateTransitionException refused = assertThrows(InvalidStateTransitionException.class,
                () -> feedbackService.submitFeedback(incident.getId(), 5, "Great", user("emp1")));

        assertEquals(HttpStatus.CONFLICT, refused.getStatus());
        assertEquals(IncidentStatus.RESOLVED, reload(incident).getStatus());
        assertTrue(feedbacks.findByIncidentId(incident.getId()).isEmpty());
    }

    @Test
    void ratingsOfZeroAndSixAreRefusedAndTheIncidentStaysVerified() {
        Incident incident = verifiedIncident();

        assertThrows(InvalidInputException.class, () -> feedbackService.submitFeedback(incident.getId(), 0, "x", user("emp1")));
        assertThrows(InvalidInputException.class, () -> feedbackService.submitFeedback(incident.getId(), 6, "x", user("emp1")));

        assertEquals(IncidentStatus.VERIFIED, reload(incident).getStatus());
        assertTrue(feedbacks.findByIncidentId(incident.getId()).isEmpty());
    }

    @Test
    void onlyTheReporterCanGiveFeedback() {
        Incident incident = verifiedIncident();

        UnauthorizedOperationException refused = assertThrows(UnauthorizedOperationException.class,
                () -> feedbackService.submitFeedback(incident.getId(), 5, "x", user("tech1")));
        assertThrows(UnauthorizedOperationException.class, () -> feedbackService.submitFeedback(incident.getId(), 5, "x", user("admin")));
        assertThrows(UnauthorizedOperationException.class, () -> feedbackService.submitFeedback(incident.getId(), 5, "x", user("emp2")));

        assertEquals(HttpStatus.FORBIDDEN, refused.getStatus());
        assertEquals(IncidentStatus.VERIFIED, reload(incident).getStatus());
        assertTrue(feedbacks.findByIncidentId(incident.getId()).isEmpty());
    }
}
