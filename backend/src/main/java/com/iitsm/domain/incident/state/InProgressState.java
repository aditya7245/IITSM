package com.iitsm.domain.incident.state;

import static com.iitsm.domain.incident.IncidentStatus.*;

final class InProgressState extends AbstractIncidentState {
    InProgressState() {
        super(IN_PROGRESS, PENDING_INFO, RESOLVED);
    }
}
