package com.iitsm.domain.incident.state;

import static com.iitsm.domain.incident.IncidentStatus.*;

final class CancelledState extends AbstractIncidentState {
    CancelledState() {
        super(CANCELLED);
    }
}
