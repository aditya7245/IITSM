package com.iitsm.service;

import com.iitsm.domain.audit.AuditLog;
import com.iitsm.domain.incident.CommentType;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentComment;
import com.iitsm.domain.incident.IncidentStatus;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Demo workflow 2, middle section, against the real database and seeded data: the technician asks for more
 * information (the SLA clock pauses), the reporter answers (the clock resumes and both deadlines move later by
 * the waiting time). Rolls back after each test. Assumes the default time scale of 1.
 */
@Transactional
class IncidentCommentServiceIntegrationTest extends AbstractIntegrationTest {

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

    /** Reports the payment outage as emp1 (P1: respond in 5 minutes, resolve in 30), assigned to tech1. */
    private Incident reportAndStartWork() {
        CreateIncidentCommand command = new CreateIncidentCommand("Payment application outage",
                "Users cannot complete payment transactions",
                categories.findByName("Application").orElseThrow().getId(),
                services.findByName("Payment Application").orElseThrow().getId(),
                (short) 3, (short) 3, 250);
        Incident incident = incidentService.create(command, user("emp1")).incident();
        clock.advance(Duration.ofMinutes(1));
        incidentService.acknowledge(incident.getId(), user("tech1"));
        incidentService.startProgress(incident.getId(), user("tech1"));
        return incident;
    }

    private Incident reload(Incident incident) {
        incidents.flush();
        em.clear();
        return incidents.findById(incident.getId()).orElseThrow();
    }

    private List<String> bodies(List<IncidentComment> comments) {
        return comments.stream().map(IncidentComment::getBody).toList();
    }

    @Test
    void theSlaClockPausesWhileWaitingAndBothDeadlinesMoveLaterByTheWaitingTime() {
        Incident incident = reportAndStartWork();
        Long id = incident.getId();
        var originalResponse = incident.getResponseDeadline();
        var originalResolution = incident.getResolutionDeadline();
        assertEquals(TestClockConfig.START.plus(Duration.ofMinutes(30)), originalResolution);

        clock.advance(Duration.ofMinutes(1));                                      // now START + 2 minutes
        commentService.requestInfo(id, "Which users are affected, and since when?", user("tech1"));

        Incident waiting = reload(incident);
        assertEquals(IncidentStatus.PENDING_INFO, waiting.getStatus());
        assertEquals(TestClockConfig.START.plus(Duration.ofMinutes(2)), waiting.getSlaPausedAt());
        assertEquals(originalResolution, waiting.getResolutionDeadline());        // not moved yet

        clock.advance(Duration.ofMinutes(15));                                     // the reporter takes 15 minutes
        commentService.respondToInfo(id, "Everyone in Finance, since 9 o'clock", user("emp1"));

        Incident resumed = reload(incident);
        assertEquals(IncidentStatus.IN_PROGRESS, resumed.getStatus());
        assertNull(resumed.getSlaPausedAt());
        assertEquals(900, resumed.getSlaPausedSeconds());
        assertEquals(originalResponse.plus(Duration.ofMinutes(15)), resumed.getResponseDeadline());
        assertEquals(originalResolution.plus(Duration.ofMinutes(15)), resumed.getResolutionDeadline());
    }

    @Test
    void theAuditHistoryShowsTheRequestAndTheAnswer() {
        Incident incident = reportAndStartWork();
        commentService.requestInfo(incident.getId(), "Which users are affected?", user("tech1"));
        clock.advance(Duration.ofMinutes(5));
        commentService.respondToInfo(incident.getId(), "Everyone in Finance", user("emp1"));

        List<AuditLog> history = auditService.history(incident.getId());

        assertEquals(List.of("CREATED", "CLASSIFIED", "ASSIGNED", "ACKNOWLEDGED", "INVESTIGATION_STARTED",
                "INFO_REQUESTED", "INFO_RECEIVED"), history.stream().map(AuditLog::getAction).toList());
        AuditLog requested = history.get(5);
        assertEquals("IN_PROGRESS", requested.getPreviousState());
        assertEquals("PENDING_INFO", requested.getNewState());
        AuditLog received = history.get(6);
        assertEquals("PENDING_INFO", received.getPreviousState());
        assertEquals("IN_PROGRESS", received.getNewState());
    }

    @Test
    void theReporterSeesOnlyTheConversationWhileStaffSeeTheInternalNotesToo() {
        Incident incident = reportAndStartWork();
        Long id = incident.getId();
        commentService.addNote(id, "Internal: gateway logs show timeouts", user("tech1"));
        commentService.requestInfo(id, "Which users are affected?", user("tech1"));
        commentService.respondToInfo(id, "Everyone in Finance", user("emp1"));
        commentService.addNote(id, "Internal: vendor informed", user("admin"));
        em.flush();
        em.clear();

        List<IncidentComment> forReporter = commentService.listComments(id, user("emp1"));
        List<IncidentComment> forTechnician = commentService.listComments(id, user("tech1"));
        List<IncidentComment> forAdmin = commentService.listComments(id, user("admin"));

        assertEquals(List.of("Which users are affected?", "Everyone in Finance"), bodies(forReporter));
        assertEquals(List.of("Internal: gateway logs show timeouts", "Which users are affected?",
                "Everyone in Finance", "Internal: vendor informed"), bodies(forTechnician));
        assertEquals(bodies(forTechnician), bodies(forAdmin));
        assertEquals(List.of(CommentType.NOTE, CommentType.INFO_REQUEST, CommentType.INFO_RESPONSE, CommentType.NOTE),
                forAdmin.stream().map(IncidentComment::getType).toList());
        assertEquals("emp1@iitsm.local", forReporter.get(1).getAuthor().getEmail());
    }

    @Test
    void anAnswerWhileNobodyIsWaitingIsOnlySavedAsAComment() {
        Incident incident = reportAndStartWork();                    // IN_PROGRESS, not waiting
        var deadline = incident.getResolutionDeadline();

        commentService.respondToInfo(incident.getId(), "One more detail: it is the card payments", user("emp1"));

        Incident same = reload(incident);
        assertEquals(IncidentStatus.IN_PROGRESS, same.getStatus());
        assertEquals(deadline, same.getResolutionDeadline());
        assertEquals(0, same.getSlaPausedSeconds());
        assertEquals(List.of("One more detail: it is the card payments"),
                bodies(commentService.listComments(incident.getId(), user("emp1"))));
        assertFalse(auditService.history(incident.getId()).stream().anyMatch(log -> log.getAction().equals("INFO_RECEIVED")));
    }

    @Test
    void theWrongPeopleAreRefused() {
        Incident incident = reportAndStartWork();
        Long id = incident.getId();

        assertThrows(UnauthorizedOperationException.class, () -> commentService.requestInfo(id, "Why?", user("tech2")));
        assertThrows(UnauthorizedOperationException.class, () -> commentService.addNote(id, "x", user("emp1")));
        assertThrows(UnauthorizedOperationException.class, () -> commentService.listComments(id, user("emp2")));

        assertEquals(IncidentStatus.IN_PROGRESS, reload(incident).getStatus());
    }

    @Test
    void aClosedIncidentRefusesAnswers() {
        Incident incident = reportAndStartWork();
        Long id = incident.getId();
        incidentService.resolve(id, "Restarted the payment gateway", user("tech1"));
        incidentService.verify(id, user("emp1"));
        incidentService.close(id, user("admin"));

        InvalidStateTransitionException refused = assertThrows(InvalidStateTransitionException.class,
                () -> commentService.respondToInfo(id, "Thanks!", user("emp1")));

        assertEquals(HttpStatus.CONFLICT, refused.getStatus());
    }
}
