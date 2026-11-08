package com.iitsm.notification;

import com.iitsm.domain.user.Employee;
import com.iitsm.observer.IncidentEventType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NotificationMessageTest {

    private final Employee user = new Employee("emp@iitsm.local", "hash", "Emp", "Finance");

    @Test
    void recipientTypeAndTextAreRequiredButTheIncidentIsOptional() {
        NotificationMessage m = new NotificationMessage(user, null, IncidentEventType.CREATED, "hello");
        assertEquals("hello", m.text());
        assertThrows(NullPointerException.class, () -> new NotificationMessage(null, null, IncidentEventType.CREATED, "x"));
        assertThrows(NullPointerException.class, () -> new NotificationMessage(user, null, null, "x"));
        assertThrows(NullPointerException.class, () -> new NotificationMessage(user, null, IncidentEventType.CREATED, null));
    }

    @Test
    void everyEventTypeHasAShortTitle() {
        for (IncidentEventType type : IncidentEventType.values()) {
            assertNotNull(type.title(), type.name());
            assertFalse(type.title().isBlank(), type.name());
        }
        assertEquals("Incident created", IncidentEventType.CREATED.title());
    }
}
