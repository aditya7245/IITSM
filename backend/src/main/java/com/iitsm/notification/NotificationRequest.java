package com.iitsm.notification;

import com.iitsm.observer.IncidentEventType;

import java.util.List;

/**
 * What the notification code needs to know about an event, using only ids and text.
 * Notifications are sent later on another thread, where database objects from the original request must not be
 * used, so everything is looked up again from these ids.
 */
public record NotificationRequest(IncidentEventType type, Long incidentId, String description,
                                  List<Long> explicitRecipientIds) {

    public NotificationRequest {
        explicitRecipientIds = explicitRecipientIds == null ? List.of() : List.copyOf(explicitRecipientIds);
    }
}
