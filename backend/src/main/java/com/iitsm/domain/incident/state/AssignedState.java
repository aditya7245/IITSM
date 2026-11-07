package com.iitsm.domain.incident.state;

import static com.iitsm.domain.incident.IncidentStatus.*;

final class AssignedState extends AbstractIncidentState {
    AssignedState() {
        super(ASSIGNED, ACKNOWLEDGED, CANCELLED, DUPLICATE);
    }
}
