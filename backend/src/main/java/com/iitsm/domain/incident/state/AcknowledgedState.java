package com.iitsm.domain.incident.state;

import static com.iitsm.domain.incident.IncidentStatus.*;

final class AcknowledgedState extends AbstractIncidentState {
    AcknowledgedState() {
        super(ACKNOWLEDGED, IN_PROGRESS, DUPLICATE);
    }
}
