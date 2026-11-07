package com.iitsm.domain.incident.state;

import static com.iitsm.domain.incident.IncidentStatus.*;

final class VerifiedState extends AbstractIncidentState {
    VerifiedState() {
        super(VERIFIED, CLOSED);
    }
}
