package com.iitsm.observer;

import com.iitsm.domain.audit.AuditLog;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentStatus;
import com.iitsm.domain.user.User;
import com.iitsm.repository.*;
import com.iitsm.service.AuditService;
import com.iitsm.service.ConfigService;
import com.iitsm.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Not marked @Transactional at class level: the rollback test needs real transaction boundaries.
 * Every test either cleans up after itself or runs inside its own transaction that is rolled back.
 */
class AuditObserverTest extends AbstractIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-11-10T09:00:00Z");

    @Autowired IncidentEventPublisher publisher;
    @Autowired AuditService auditService;
    @Autowired IncidentRepository incidents;
    @Autowired UserRepository users;
    @Autowired ITServiceRepository services;
    @Autowired CategoryRepository categories;
    @Autowired ConfigService config;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactionManager;

    TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
    }

    /** Runs the body in a transaction that is always rolled back, so nothing it writes survives the test. */
    private void inRolledBackTransaction(Runnable body) {
        tx.executeWithoutResult(status -> {
            body.run();
            status.setRollbackOnly();
        });
    }

    private Incident savedIncident(String number) {
        User reporter = users.findByEmailIgnoreCase("emp1@iitsm.local").orElseThrow();
        return incidents.saveAndFlush(new Incident(number, "Payment down", "Cannot pay", reporter,
                services.findByName("Payment Application").orElseThrow(),
                categories.findByName("Application").orElseThrow(), (short) 3, (short) 3, 10, T0));
    }

    @Test
    void publishingAnEventWritesOneHistoryLineWithAllFields() {
        inRolledBackTransaction(() -> {
            Incident incident = savedIncident("AUD-1");
            User reporter = incident.getReporter();

            publisher.publish(IncidentEvent.of(IncidentEventType.CREATED, incident, reporter,
                    null, IncidentStatus.NEW, "Created by employee", T0));
            em.flush();
            em.clear();

            List<AuditLog> history = auditService.history(incident.getId());
            assertEquals(1, history.size());
            AuditLog line = history.get(0);
            assertEquals("CREATED", line.getAction());
            assertNull(line.getPreviousState());
            assertEquals("NEW", line.getNewState());
            assertEquals("Created by employee", line.getDescription());
            assertEquals(reporter.getId(), line.getUser().getId());
            assertEquals(incident.getId(), line.getIncident().getId());
            assertEquals(T0, line.getCreatedAt());
        });
    }

    @Test
    void systemEventsHaveNoUserAndStatusChangesAreRecorded() {
        inRolledBackTransaction(() -> {
            Incident incident = savedIncident("AUD-2");

            publisher.publish(IncidentEvent.of(IncidentEventType.CLASSIFIED, incident, null,
                    IncidentStatus.NEW, IncidentStatus.CLASSIFIED, "System classified it", T0.plusSeconds(1)));
            em.flush();
            em.clear();

            AuditLog line = auditService.history(incident.getId()).get(0);
            assertNull(line.getUser());
            assertEquals("NEW", line.getPreviousState());
            assertEquals("CLASSIFIED", line.getNewState());
        });
    }

    @Test
    void historyIsOrderedOldestFirstByEventTime() {
        inRolledBackTransaction(() -> {
            Incident incident = savedIncident("AUD-3");

            // published in the "wrong" order on purpose
            publisher.publish(IncidentEvent.of(IncidentEventType.ASSIGNED, incident, null,
                    IncidentStatus.CLASSIFIED, IncidentStatus.ASSIGNED, "second", T0.plusSeconds(20)));
            publisher.publish(IncidentEvent.of(IncidentEventType.CLASSIFIED, incident, null,
                    IncidentStatus.NEW, IncidentStatus.CLASSIFIED, "first", T0.plusSeconds(10)));
            em.flush();
            em.clear();

            List<AuditLog> history = auditService.history(incident.getId());
            assertEquals(List.of("first", "second"), history.stream().map(AuditLog::getDescription).toList());
        });
    }

    @Test
    void historyOfAnIncidentWithNoEventsIsEmpty() {
        inRolledBackTransaction(() -> {
            Incident incident = savedIncident("AUD-4");
            assertTrue(auditService.history(incident.getId()).isEmpty());
        });
    }

    @Test
    void aFailedHistoryWriteRollsBackTheChangeThatCausedTheEvent() {
        // An incident that was never saved cannot be referenced by a history line, so the audit write fails.
        Incident unsaved = new Incident("AUD-X", "t", "d", null, null, null, (short) 2, (short) 2, 1, T0);

        assertThrows(Exception.class, () -> tx.executeWithoutResult(status -> {
            config.set("audit.rollback.test", "changed");   // the "business change"
            publisher.publish(IncidentEvent.of(IncidentEventType.CREATED, unsaved, null,
                    null, IncidentStatus.NEW, "will fail", T0));
        }));

        assertEquals("missing", config.getString("audit.rollback.test", "missing"));
    }

    @Test
    void historyLinesCannotBeChangedOrDeleted() {
        // each statement gets its own rolled-back transaction, because a failed statement aborts the transaction
        Long[] incidentId = new Long[1];
        inRolledBackTransaction(() -> {
            Incident incident = savedIncident("AUD-5");
            incidentId[0] = incident.getId();
            publisher.publish(IncidentEvent.of(IncidentEventType.CREATED, incident, null,
                    null, IncidentStatus.NEW, "to protect", T0));
            em.flush();

            assertThrows(DataAccessException.class,
                    () -> jdbc.update("update audit_logs set action = 'TAMPERED' where incident_id = ?", incident.getId()));
        });

        inRolledBackTransaction(() -> {
            Incident incident = savedIncident("AUD-6");
            publisher.publish(IncidentEvent.of(IncidentEventType.CREATED, incident, null,
                    null, IncidentStatus.NEW, "to protect", T0));
            em.flush();

            assertThrows(DataAccessException.class,
                    () -> jdbc.update("delete from audit_logs where incident_id = ?", incident.getId()));
        });
    }
}
