package com.iitsm.notification;

import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentStatus;
import com.iitsm.domain.user.User;
import com.iitsm.observer.IncidentEvent;
import com.iitsm.observer.IncidentEventPublisher;
import com.iitsm.observer.IncidentEventType;
import com.iitsm.repository.*;
import com.iitsm.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * End to end with a REAL commit: the event is published inside a transaction, and only after that transaction
 * commits does the background thread create the notification. This test commits data, so it cleans up after itself.
 */
class NotificationAsyncWiringTest extends AbstractIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-11-10T09:00:00Z");
    private static final String NUMBER = "ASYNC-1";

    @Autowired IncidentEventPublisher publisher;
    @Autowired IncidentRepository incidents;
    @Autowired UserRepository users;
    @Autowired ITServiceRepository services;
    @Autowired CategoryRepository categories;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        jdbc.execute("truncate table audit_logs");   // history rows are protected from DELETE, but this is a throwaway test database
        jdbc.update("delete from notifications where incident_id in (select id from incidents where incident_number = ?)", NUMBER);
        jdbc.update("delete from incidents where incident_number = ?", NUMBER);
    }

    private long notificationCount() {
        return jdbc.queryForObject("select count(*) from notifications n join incidents i on i.id = n.incident_id "
                + "where i.incident_number = ?", Long.class, NUMBER);
    }

    @Test
    void aCommittedEventCreatesTheNotificationInTheBackground() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            User reporter = users.findByEmailIgnoreCase("emp1@iitsm.local").orElseThrow();
            Incident incident = incidents.saveAndFlush(new Incident(NUMBER, "Wiki down", "Cannot open wiki", reporter,
                    services.findByName("Internal Wiki").orElseThrow(),
                    categories.findByName("Application").orElseThrow(), (short) 1, (short) 1, 1, T0));

            publisher.publish(IncidentEvent.of(IncidentEventType.CREATED, incident, reporter,
                    null, IncidentStatus.NEW, "Created by employee", T0));
        });

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertEquals(1, notificationCount()));
    }
}
