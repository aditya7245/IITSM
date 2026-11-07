package com.iitsm.domain.incident.state;

import static com.iitsm.domain.incident.IncidentStatus.*;

final class ClassifiedState extends AbstractIncidentState {
    ClassifiedState() {
        super(CLASSIFIED, ASSIGNED, CANCELLED, DUPLICATE);
    }
}
