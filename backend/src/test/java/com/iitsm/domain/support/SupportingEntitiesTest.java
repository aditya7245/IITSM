package com.iitsm.domain.support;

import com.iitsm.domain.audit.AuditLog;
import com.iitsm.domain.incident.*;
import com.iitsm.domain.notification.Notification;
import com.iitsm.domain.user.User;
import com.iitsm.exception.InvalidInputException;
import com.iitsm.repository.*;
import com.iitsm.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Saves and reloads one of each supporting entity so Hibernate checks the mappings. Rolls back after each test. */
@Transactional
class SupportingEntitiesTest extends AbstractIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-11-10T09:00:00Z");

    @Autowired IncidentRepository incidents;
    @Autowired IncidentCommentRepository comments;
    @Autowired FeedbackRepository feedback;
    @Autowired AuditLogRepository audit;
    @Autowired NotificationRepository notifications;
    @Autowired UserRepository users;
    @Autowired ITServiceRepository services;
    @Autowired CategoryRepository categories;
    @Autowired EntityManager em;

    User reporter;
    User technician;
    Incident incident;

    @BeforeEach
    void createIncident() {
        reporter = users.findByEmailIgnoreCase("emp1@iitsm.local").orElseThrow();
        technician = users.findByEmailIgnoreCase("tech1@iitsm.local").orElseThrow();
        incident = incidents.saveAndFlush(new Incident("TEST-S1", "Payment down", "Cannot pay", reporter,
                services.findByName("Payment Application").orElseThrow(),
                categories.findByName("Application").orElseThrow(), (short) 3, (short) 3, 10, T0));
    }

    @Test
    void commentSavesAndListsInOrder() {
        comments.save(new IncidentComment(incident, technician, CommentType.INFO_REQUEST, "Which browser?"));
        comments.saveAndFlush(new IncidentComment(incident, reporter, CommentType.INFO_RESPONSE, "Chrome"));
        em.clear();

        List<IncidentComment> thread = comments.findByIncidentIdOrderByCreatedAtAscIdAsc(incident.getId());
        assertEquals(2, thread.size());
        assertEquals(CommentType.INFO_REQUEST, thread.get(0).getType());
        assertEquals("Chrome", thread.get(1).getBody());
        assertEquals(reporter.getId(), thread.get(1).getAuthor().getId());
        assertNotNull(thread.get(0).getCreatedAt());
    }

    @Test
    void blankCommentIsRejected() {
        assertThrows(InvalidInputException.class,
                () -> new IncidentComment(incident, technician, CommentType.NOTE, " "));
    }

    @Test
    void feedbackSavesAndAllowsOnlyOnePerIncident() {
        feedback.saveAndFlush(new Feedback(incident, (short) 4, "Quick fix"));
        em.clear();

        Feedback loaded = feedback.findByIncidentId(incident.getId()).orElseThrow();
        assertEquals(4, loaded.getRating());
        assertEquals("Quick fix", loaded.getComments());
        assertTrue(feedback.existsByIncidentId(incident.getId()));

        assertThrows(DataIntegrityViolationException.class,
                () -> feedback.saveAndFlush(new Feedback(incident, (short) 5, null)));
    }

    @Test
    void feedbackRatingMustBeBetween1And5() {
        assertThrows(InvalidInputException.class, () -> new Feedback(incident, (short) 0, null));
        assertThrows(InvalidInputException.class, () -> new Feedback(incident, (short) 6, null));
    }

    @Test
    void auditLogSavesWithAndWithoutAUser() {
        audit.save(new AuditLog(incident, reporter, "CREATED", null, "NEW", "Created by employee", T0));
        audit.saveAndFlush(new AuditLog(incident, null, "CLASSIFIED", "NEW", "CLASSIFIED", "System", T0.plusSeconds(1)));
        em.clear();

        List<AuditLog> history = audit.findByIncidentIdOrderByCreatedAtAscIdAsc(incident.getId());
        assertEquals(2, history.size());
        assertEquals("CREATED", history.get(0).getAction());
        assertNull(history.get(0).getPreviousState());
        assertEquals(reporter.getId(), history.get(0).getUser().getId());
        assertEquals(T0, history.get(0).getCreatedAt());
        assertNull(history.get(1).getUser());
        assertEquals("CLASSIFIED", history.get(1).getNewState());
    }

    @Test
    void notificationSavesCountsAsUnreadAndCanBeMarkedRead() {
        Notification n = notifications.saveAndFlush(
                new Notification(reporter, incident, "CREATED", "TEST-S1 was created"));
        assertEquals(1, notifications.countByUserIdAndReadFalse(reporter.getId()));

        n.markRead();
        notifications.saveAndFlush(n);
        em.clear();

        assertEquals(0, notifications.countByUserIdAndReadFalse(reporter.getId()));
        var page = notifications.findByUserIdOrderByCreatedAtDesc(reporter.getId(), PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());
        assertTrue(page.getContent().get(0).isRead());
        assertEquals("TEST-S1 was created", page.getContent().get(0).getMessage());
    }
}
