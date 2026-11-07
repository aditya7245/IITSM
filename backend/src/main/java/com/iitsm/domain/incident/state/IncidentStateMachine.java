package com.iitsm.domain.incident.state;

import com.iitsm.domain.incident.IncidentStatus;
import com.iitsm.exception.InvalidStateTransitionException;

import java.util.Objects;

/** The single place that decides whether an incident may move from one status to another. */
public final class IncidentStateMachine {

    private IncidentStateMachine() { }

    public static boolean canTransition(IncidentStatus from, IncidentStatus to) {
        return IncidentStates.of(Objects.requireNonNull(from)).canMoveTo(Objects.requireNonNull(to));
    }

    /** @throws InvalidStateTransitionException if the move is not allowed, for example NEW to CLOSED */
    public static void requireTransition(IncidentStatus from, IncidentStatus to) {
        if (!canTransition(from, to)) {
            throw new InvalidStateTransitionException(from.name(), to.name());
        }
    }
}
