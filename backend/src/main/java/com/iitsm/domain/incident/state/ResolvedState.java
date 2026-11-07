package com.iitsm.domain.incident.state;

import static com.iitsm.domain.incident.IncidentStatus.*;

final class ResolvedState extends AbstractIncidentState {
    ResolvedState() {
        super(RESOLVED, VERIFIED, IN_PROGRESS);
    }
}
