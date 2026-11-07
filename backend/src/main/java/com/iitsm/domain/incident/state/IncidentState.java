package com.iitsm.domain.incident.state;

import com.iitsm.domain.incident.IncidentStatus;

import java.util.Set;

/**
 * State pattern: one implementation per incident status. Each state knows which statuses
 * an incident may move to next, so the rules live in one small class per status instead of a big if/else.
 */
public interface IncidentState {

    /** The status this state represents. */
    IncidentStatus status();

    /** The statuses an incident in this state may move to (empty for final states). */
    Set<IncidentStatus> allowedNext();

    default boolean canMoveTo(IncidentStatus target) {
        return allowedNext().contains(target);
    }
}
