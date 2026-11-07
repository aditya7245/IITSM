package com.iitsm.domain.incident.state;

import static com.iitsm.domain.incident.IncidentStatus.*;

final class PendingInfoState extends AbstractIncidentState {
    PendingInfoState() {
        super(PENDING_INFO, IN_PROGRESS);
    }
}
