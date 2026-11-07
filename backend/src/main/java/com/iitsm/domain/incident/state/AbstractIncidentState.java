package com.iitsm.domain.incident.state;

import com.iitsm.domain.incident.IncidentStatus;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Shared plumbing for the concrete states: a status and a fixed, read-only set of allowed next statuses. */
abstract class AbstractIncidentState implements IncidentState {

    private final IncidentStatus status;
    private final Set<IncidentStatus> allowedNext;

    AbstractIncidentState(IncidentStatus status, IncidentStatus... allowedNext) {
        this.status = status;
        EnumSet<IncidentStatus> next = EnumSet.noneOf(IncidentStatus.class);
        Collections.addAll(next, allowedNext);
        this.allowedNext = Collections.unmodifiableSet(next);
    }

    @Override
    public IncidentStatus status() {
        return status;
    }

    @Override
    public Set<IncidentStatus> allowedNext() {
        return allowedNext;
    }
}
