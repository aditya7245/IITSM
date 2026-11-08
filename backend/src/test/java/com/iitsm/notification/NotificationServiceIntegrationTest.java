package com.iitsm.notification;

import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.Priority;
import com.iitsm.domain.user.Technician;
import com.iitsm.domain.user.User;
import com.iitsm.observer.IncidentEventType;
import com.iitsm.repository.*;
import com.iitsm.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The real service with the seeded users. Rolls back after each test, so no notifications are left behind. */
@Transactional
class NotificationServiceIntegrationTest extends AbstractIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-11-10T09:00:00Z");

    @Autowired NotificationService service;
    @Autowired NotificationRepository notifications;
    @Autowired IncidentRepository incidents;
    @Autowired UserRepository users;
    @Autowired ITServiceRepository services;
    @Autowired CategoryRepository categories;

    User reporter;      // emp1
    Technician tech;    // tech1, in the Application Support team led by lead.app
    User lead;
    User admin;
    User manager;
    Incident incident;

    @BeforeEach
    void setUp() {
        reporter = users.findByEmailIgnoreCase("emp1@iitsm.local").orElseThrow();
        tech = (Technician) users.findByEmailIgnoreCase("tech1@iitsm.local").orElseThrow();
        lead = users.findByEmailIgnoreCase("lead.app@iitsm.local").orElseThrow();
        admin = users.findByEmailIgnoreCase("admin@iitsm.local").orElseThrow();
        manager = users.findByEmailIgnoreCase("manager@iitsm.local").orElseThrow();

        incident = new Incident("NS-1", "Payment down", "Cannot pay", reporter,
                services.findByName("Payment Application").orElseThrow(),
                categories.findByName("Application").orElseThrow(), (short) 3, (short) 3, 10, T0);
        incident.classify(Priority.P1, 27, T0.plusSeconds(300), T0.plusSeconds(1800));
        incident.assignTo(tech);
        incident = incidents.saveAndFlush(incident);
    }

    private long unread(User user) {
        return notifications.countByUserIdAndReadFalse(user.getId());
    }

    private NotificationRequest request(IncidentEventType type, List<Long> explicit) {
        return new NotificationRequest(type, incident.getId(), "details", explicit);
    }

    @Test
    void createdNotifiesOnlyTheReporter() {
        service.notify(request(IncidentEventType.CREATED, List.of()));
        assertEquals(1, unread(reporter));
        assertEquals(0, unread(tech));
    }

    @Test
    void assignedNotifiesTheAssigneeAndTheReporter() {
        service.notify(request(IncidentEventType.ASSIGNED, List.of()));
        assertEquals(1, unread(tech));
        assertEquals(1, unread(reporter));
        assertEquals(0, unread(lead));
    }

    @Test
    void slaWarningNotifiesTheAssigneeAndTheTeamLead() {
        service.notify(request(IncidentEventType.SLA_WARNING, List.of()));
        assertEquals(1, unread(tech));
        assertEquals(1, unread(lead));
        assertEquals(0, unread(reporter));
    }

    @Test
    void failedAssignmentNotifiesTheAdministratorAndTheItManager() {
        service.notify(request(IncidentEventType.ASSIGNMENT_FAILED, List.of()));
        assertEquals(1, unread(admin));
        assertEquals(1, unread(manager));
        assertEquals(0, unread(tech));
    }

    @Test
    void escalationNotifiesOnlyTheExplicitRecipients() {
        service.notify(request(IncidentEventType.ESCALATED, List.of(lead.getId())));
        assertEquals(1, unread(lead));
        assertEquals(0, unread(reporter));
        assertEquals(0, unread(tech));
    }

    @Test
    void anEventWithNobodyToNotifySavesNothing() {
        long before = notifications.count();
        service.notify(request(IncidentEventType.CLASSIFIED, List.of()));
        assertEquals(before, notifications.count());
    }

    @Test
    void theMessageNamesTheIncidentTheEventAndTheDetails() {
        service.notify(request(IncidentEventType.RESOLVED, List.of()));
        String message = notifications.findByUserIdOrderByCreatedAtDesc(reporter.getId(),
                org.springframework.data.domain.PageRequest.of(0, 5)).getContent().get(0).getMessage();
        assertEquals("NS-1: Incident resolved - details", message);
    }

    @Test
    void anUnknownIncidentIsIgnoredWithoutError() {
        long before = notifications.count();
        service.notify(new NotificationRequest(IncidentEventType.CREATED, 999_999_999L, "x", List.of()));
        assertEquals(before, notifications.count());
    }
}
