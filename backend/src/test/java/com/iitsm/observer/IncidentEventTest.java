package com.iitsm.observer;

import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IncidentEventTest {

    private static final Instant T0 = Instant.parse("2026-11-10T09:00:00Z");

    private static Incident incident() {
        return new Incident("INC1", "t", "d", null, null, null, (short) 2, (short) 2, 1, T0);
    }

    @Test
    void ofCreatesAnEventWithNoExplicitRecipients() {
        IncidentEvent e = IncidentEvent.of(IncidentEventType.CLASSIFIED, incident(), null,
                IncidentStatus.NEW, IncidentStatus.CLASSIFIED, "System classified it", T0);
        assertEquals(IncidentEventType.CLASSIFIED, e.type());
        assertNull(e.actor());
        assertEquals(IncidentStatus.NEW, e.previous());
        assertEquals(IncidentStatus.CLASSIFIED, e.current());
        assertEquals(T0, e.at());
        assertTrue(e.explicitRecipientIds().isEmpty());
    }

    @Test
    void nullRecipientListBecomesEmpty() {
        IncidentEvent e = new IncidentEvent(IncidentEventType.CREATED, incident(), null, null,
                IncidentStatus.NEW, "created", T0, null);
        assertTrue(e.explicitRecipientIds().isEmpty());
    }

    @Test
    void explicitRecipientsAreCopiedSoLaterChangesDoNotLeakIn() {
        List<Long> ids = new ArrayList<>(List.of(5L, 6L));
        IncidentEvent e = new IncidentEvent(IncidentEventType.ESCALATED, incident(), null,
                IncidentStatus.ASSIGNED, IncidentStatus.ASSIGNED, "escalated", T0, ids);
        ids.add(7L);
        assertEquals(List.of(5L, 6L), e.explicitRecipientIds());
        assertThrows(UnsupportedOperationException.class, () -> e.explicitRecipientIds().add(8L));
    }

    @Test
    void typeIncidentAndTimeAreRequired() {
        assertThrows(NullPointerException.class, () -> IncidentEvent.of(null, incident(), null, null, IncidentStatus.NEW, "x", T0));
        assertThrows(NullPointerException.class, () -> IncidentEvent.of(IncidentEventType.CREATED, null, null, null, IncidentStatus.NEW, "x", T0));
        assertThrows(NullPointerException.class, () -> IncidentEvent.of(IncidentEventType.CREATED, incident(), null, null, IncidentStatus.NEW, "x", null));
    }

    @Test
    void thereIsAnEventTypeForEveryThingTheNotificationRulesMention() {
        assertEquals(18, IncidentEventType.values().length);
        assertNotNull(IncidentEventType.valueOf("SLA_VIOLATED"));
        assertNotNull(IncidentEventType.valueOf("ASSIGNMENT_FAILED"));
    }
}
