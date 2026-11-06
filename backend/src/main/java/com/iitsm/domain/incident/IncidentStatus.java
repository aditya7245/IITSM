package com.iitsm.domain.incident;

import java.util.EnumSet;
import java.util.Set;

/** Where an incident currently is in its lifecycle. Allowed moves between statuses live in the state machine (item 2.2). */
public enum IncidentStatus {
    NEW,
    CLASSIFIED,
    ASSIGNED,
    ACKNOWLEDGED,
    IN_PROGRESS,
    PENDING_INFO,
    RESOLVED,
    VERIFIED,
    CLOSED,
    CANCELLED,
    DUPLICATE;

    private static final Set<IncidentStatus> TERMINAL = EnumSet.of(CLOSED, CANCELLED, DUPLICATE);
    private static final Set<IncidentStatus> WORKLOAD = EnumSet.of(ASSIGNED, ACKNOWLEDGED, IN_PROGRESS, PENDING_INFO);

    /** No further transitions are possible from a terminal status. */
    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    /** An incident is open while it is not in a terminal status. */
    public boolean isOpen() {
        return !isTerminal();
    }

    /** True while the incident counts against its assignee's workload limit. */
    public boolean countsTowardWorkload() {
        return WORKLOAD.contains(this);
    }

    /** All statuses that count toward workload, for use in repository queries. */
    public static Set<IncidentStatus> workloadStatuses() {
        return EnumSet.copyOf(WORKLOAD);
    }

    /** All terminal statuses, for use in repository queries. */
    public static Set<IncidentStatus> terminalStatuses() {
        return EnumSet.copyOf(TERMINAL);
    }
}
