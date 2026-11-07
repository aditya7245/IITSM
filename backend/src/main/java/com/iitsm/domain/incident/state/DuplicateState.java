package com.iitsm.domain.incident.state;

import static com.iitsm.domain.incident.IncidentStatus.*;

final class DuplicateState extends AbstractIncidentState {
    DuplicateState() {
        super(DUPLICATE);
    }
}
