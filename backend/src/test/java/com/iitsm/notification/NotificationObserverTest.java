package com.iitsm.observer;

import com.iitsm.domain.common.BaseEntity;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentStatus;
import com.iitsm.notification.NotificationDispatcher;
import com.iitsm.notification.NotificationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Checks the timing rules of the observer without a database: a fake dispatcher records what it is asked to do. */
class NotificationObserverTest {

    private static final Instant T0 = Instant.parse("2026-11-10T09:00:00Z");

    /** A stand-in for the real dispatcher that records requests, and can be told to fail. */
    static class FakeDispatcher extends NotificationDispatcher {
        final List<NotificationRequest> received = new ArrayList<>();
        RuntimeException failure;

        FakeDispatcher() {
            super(null);
        }

        @Override
        public void dispatch(NotificationRequest request) {
            if (failure != null) {
                throw failure;
            }
            received.add(request);
        }
    }

    private final FakeDispatcher dispatcher = new FakeDispatcher();
    private final NotificationObserver observer = new NotificationObserver(dispatcher);

    private static Incident incident(Long id) {
        Incident incident = new Incident("INC1", "t", "d", null, null, null, (short) 2, (short) 2, 1, T0);
        if (id != null) {
            try {
                Field f = BaseEntity.class.getDeclaredField("id");
                f.setAccessible(true);
                f.set(incident, id);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }
        return incident;
    }

    private static IncidentEvent event(Incident incident, List<Long> explicit) {
        return new IncidentEvent(IncidentEventType.ESCALATED, incident, null, IncidentStatus.ASSIGNED,
                IncidentStatus.ASSIGNED, "level 1", T0, explicit);
    }

    @Test
    void withoutATransactionTheNotificationIsDispatchedImmediately() {
        observer.onEvent(event(incident(7L), List.of(3L, 4L)));

        assertEquals(1, dispatcher.received.size());
        NotificationRequest request = dispatcher.received.get(0);
        assertEquals(IncidentEventType.ESCALATED, request.type());
        assertEquals(7L, request.incidentId());
        assertEquals("level 1", request.description());
        assertEquals(List.of(3L, 4L), request.explicitRecipientIds());
    }

    @Test
    void insideATransactionNothingIsDispatchedUntilItCommits() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            observer.onEvent(event(incident(7L), List.of()));
            assertTrue(dispatcher.received.isEmpty());              // still inside the transaction

            for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
                s.afterCommit();                                    // the transaction commits
            }
            assertEquals(1, dispatcher.received.size());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void ifTheTransactionIsRolledBackNothingIsEverDispatched() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            observer.onEvent(event(incident(7L), List.of()));
            for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
                s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);   // rolled back: no afterCommit
            }
            assertTrue(dispatcher.received.isEmpty());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void aFailingDispatcherNeverBreaksTheCaller() {
        dispatcher.failure = new IllegalStateException("queue full");
        assertDoesNotThrow(() -> observer.onEvent(event(incident(7L), List.of())));
    }

    @Test
    void aFailingDispatcherAfterCommitNeverBreaksTheCommit() {
        dispatcher.failure = new IllegalStateException("queue full");
        TransactionSynchronizationManager.initSynchronization();
        try {
            observer.onEvent(event(incident(7L), List.of()));
            for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
                assertDoesNotThrow(s::afterCommit);
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void anIncidentThatWasNeverSavedIsSkippedWithoutError() {
        assertDoesNotThrow(() -> observer.onEvent(event(incident(null), List.of())));
        assertTrue(dispatcher.received.isEmpty());
    }
}
