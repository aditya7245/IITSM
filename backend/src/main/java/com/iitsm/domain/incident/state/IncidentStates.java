package com.iitsm.domain.incident.state;

import com.iitsm.domain.incident.IncidentStatus;

import java.util.EnumMap;
import java.util.Map;

/** Looks up the state object for a status. */
public final class IncidentStates {

    private static final Map<IncidentStatus, IncidentState> STATES = new EnumMap<>(IncidentStatus.class);

    static {
        register(new NewState());
        register(new ClassifiedState());
        register(new AssignedState());
        register(new AcknowledgedState());
        register(new InProgressState());
        register(new PendingInfoState());
        register(new ResolvedState());
        register(new VerifiedState());
        register(new ClosedState());
        register(new CancelledState());
        register(new DuplicateState());
    }

    private IncidentStates() { }

    private static void register(IncidentState state) {
        STATES.put(state.status(), state);
    }

    public static IncidentState of(IncidentStatus status) {
        return STATES.get(status);
    }
}
