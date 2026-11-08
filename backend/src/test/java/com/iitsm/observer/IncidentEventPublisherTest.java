package com.iitsm.observer;

import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.Order;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IncidentEventPublisherTest {

    private static final Instant T0 = Instant.parse("2026-11-10T09:00:00Z");

    private static IncidentEvent event() {
        Incident incident = new Incident("INC1", "t", "d", null, null, null, (short) 2, (short) 2, 1, T0);
        return IncidentEvent.of(IncidentEventType.CREATED, incident, null, null, IncidentStatus.NEW, "created", T0);
    }

    /** Records its name and the event it received into a shared log. */
    abstract static class Recording implements IncidentObserver {
        final String name;
        final List<String> log;
        final List<IncidentEvent> received = new ArrayList<>();

        Recording(String name, List<String> log) {
            this.name = name;
            this.log = log;
        }

        @Override
        public void onEvent(IncidentEvent event) {
            log.add(name);
            received.add(event);
        }
    }

    @Order(1)
    static class First extends Recording {
        First(List<String> log) { super("first", log); }
    }

    @Order(2)
    static class Second extends Recording {
        Second(List<String> log) { super("second", log); }
    }

    @Order(1)
    static class FailingFirst implements IncidentObserver {
        @Override
        public void onEvent(IncidentEvent event) {
            throw new IllegalStateException("audit write failed");
        }
    }

    @Test
    void observersAreCalledInOrderEvenIfTheListIsInTheWrongOrder() {
        List<String> log = new ArrayList<>();
        // handed over as second, first: the publisher must still run first, second
        IncidentEventPublisher publisher = new IncidentEventPublisher(List.of(new Second(log), new First(log)));

        publisher.publish(event());

        assertEquals(List.of("first", "second"), log);
    }

    @Test
    void everyObserverReceivesTheSameEvent() {
        List<String> log = new ArrayList<>();
        First first = new First(log);
        Second second = new Second(log);
        IncidentEvent e = event();

        new IncidentEventPublisher(List.of(first, second)).publish(e);

        assertSame(e, first.received.get(0));
        assertSame(e, second.received.get(0));
    }

    @Test
    void eachPublishCallsEveryObserverAgain() {
        List<String> log = new ArrayList<>();
        IncidentEventPublisher publisher = new IncidentEventPublisher(List.of(new First(log), new Second(log)));
        publisher.publish(event());
        publisher.publish(event());
        assertEquals(List.of("first", "second", "first", "second"), log);
    }

    @Test
    void anObserverFailureReachesTheCallerAndStopsTheRest() {
        List<String> log = new ArrayList<>();
        Second second = new Second(log);
        IncidentEventPublisher publisher = new IncidentEventPublisher(List.of(new FailingFirst(), second));

        assertThrows(IllegalStateException.class, () -> publisher.publish(event()));
        assertTrue(second.received.isEmpty());
    }

    @Test
    void publishingWithNoObserversDoesNothing() {
        assertDoesNotThrow(() -> new IncidentEventPublisher(List.of()).publish(event()));
    }

    @Test
    void aNullEventIsRejected() {
        assertThrows(NullPointerException.class, () -> new IncidentEventPublisher(List.of()).publish(null));
    }
}
