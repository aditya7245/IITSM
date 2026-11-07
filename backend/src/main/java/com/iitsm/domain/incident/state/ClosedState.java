package com.iitsm.domain.incident.state;

import static com.iitsm.domain.incident.IncidentStatus.*;

final class ClosedState extends AbstractIncidentState {
    ClosedState() {
        super(CLOSED);
    }
}
