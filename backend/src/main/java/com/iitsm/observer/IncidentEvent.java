package com.iitsm.observer;

import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentStatus;
import com.iitsm.domain.user.User;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * An announcement that something happened to an incident.
 *
 * @param actor                the user who did it, or null if the system did it (for example an SLA warning)
 * @param previous             the status before, or null when the incident was just created
 * @param current              the status after
 * @param explicitRecipientIds users who must be notified, overriding the normal rules; empty means "use the rules"
 *                             (only escalation uses this)
 */
public record IncidentEvent(IncidentEventType type, Incident incident, User actor, IncidentStatus previous,
                            IncidentStatus current, String description, Instant at, List<Long> explicitRecipientIds) {

    public IncidentEvent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(incident, "incident");
        Objects.requireNonNull(at, "at");
        explicitRecipientIds = explicitRecipientIds == null ? List.of() : List.copyOf(explicitRecipientIds);
    }

    /** An event that follows the normal notification rules. */
    public static IncidentEvent of(IncidentEventType type, Incident incident, User actor, IncidentStatus previous,
                                   IncidentStatus current, String description, Instant at) {
        return new IncidentEvent(type, incident, actor, previous, current, description, at, List.of());
    }
}
