package com.iitsm.notification;

import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.user.User;
import com.iitsm.observer.IncidentEventType;

import java.util.Objects;

/** One message for one person about one event, ready to be delivered by any channel. */
public record NotificationMessage(User recipient, Incident incident, IncidentEventType type, String text) {

    public NotificationMessage {
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(text, "text");
    }
}
