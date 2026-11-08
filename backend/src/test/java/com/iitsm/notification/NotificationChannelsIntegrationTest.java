package com.iitsm.notification;

import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.notification.Notification;
import com.iitsm.domain.user.User;
import com.iitsm.observer.IncidentEventType;
import com.iitsm.repository.*;
import com.iitsm.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/** The real channels and factory, wired by Spring. Rolls back after each test. */
@Transactional
class NotificationChannelsIntegrationTest extends AbstractIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-11-10T09:00:00Z");

    @Autowired NotificationChannelFactory factory;
    @Autowired NotificationRepository notifications;
    @Autowired IncidentRepository incidents;
    @Autowired UserRepository users;
    @Autowired ITServiceRepository services;
    @Autowired CategoryRepository categories;

    @Test
    void theFactoryKnowsBothRealChannels() {
        assertEquals(ChannelType.IN_APP, factory.get(ChannelType.IN_APP).type());
        assertEquals(ChannelType.EMAIL, factory.get(ChannelType.EMAIL).type());
        assertInstanceOf(InAppChannel.class, factory.get(ChannelType.IN_APP));
        assertInstanceOf(EmailChannel.class, factory.get(ChannelType.EMAIL));
    }

    @Test
    void inAppChannelSavesAnUnreadNotification() {
        User user = users.findByEmailIgnoreCase("emp1@iitsm.local").orElseThrow();
        Incident incident = incidents.saveAndFlush(new Incident("NOT-1", "Payment down", "Cannot pay", user,
                services.findByName("Payment Application").orElseThrow(),
                categories.findByName("Application").orElseThrow(), (short) 3, (short) 3, 10, T0));

        factory.get(ChannelType.IN_APP).send(
                new NotificationMessage(user, incident, IncidentEventType.CREATED, "NOT-1 was created"));

        assertEquals(1, notifications.countByUserIdAndReadFalse(user.getId()));
        Notification saved = notifications.findByUserIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(0, 5))
                .getContent().get(0);
        assertEquals("CREATED", saved.getEventType());
        assertEquals("NOT-1 was created", saved.getMessage());
        assertEquals(incident.getId(), saved.getIncident().getId());
        assertFalse(saved.isRead());
    }

    @Test
    void aVeryLongMessageIsShortenedToFitTheColumn() {
        User user = users.findByEmailIgnoreCase("emp1@iitsm.local").orElseThrow();
        String longText = "x".repeat(900);

        factory.get(ChannelType.IN_APP).send(new NotificationMessage(user, null, IncidentEventType.CREATED, longText));

        String saved = notifications.findByUserIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(0, 5))
                .getContent().get(0).getMessage();
        assertEquals(500, saved.length());
        assertTrue(saved.endsWith("..."));
    }
}
