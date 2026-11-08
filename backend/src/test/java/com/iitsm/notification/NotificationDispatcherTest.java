package com.iitsm.notification;

import com.iitsm.observer.IncidentEventType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NotificationDispatcherTest {

    /** A stand-in for the real service that records what it was asked to do, and can be told to fail. */
    static class FakeNotificationService extends NotificationService {
        final List<NotificationRequest> received = new ArrayList<>();
        RuntimeException failure;

        FakeNotificationService() {
            super(null, null, null);
        }

        @Override
        public void notify(NotificationRequest request) {
            if (failure != null) {
                throw failure;
            }
            received.add(request);
        }
    }

    private final FakeNotificationService service = new FakeNotificationService();
    private final NotificationDispatcher dispatcher = new NotificationDispatcher(service);
    private final NotificationRequest request = new NotificationRequest(IncidentEventType.CREATED, 5L, "x", List.of());

    @Test
    void passesTheRequestToTheService() {
        dispatcher.dispatch(request);
        assertEquals(List.of(request), service.received);
    }

    @Test
    void aFailureIsLoggedAndNeverEscapes() {
        service.failure = new IllegalStateException("database down");
        assertDoesNotThrow(() -> dispatcher.dispatch(request));
        assertTrue(service.received.isEmpty());
    }
}
