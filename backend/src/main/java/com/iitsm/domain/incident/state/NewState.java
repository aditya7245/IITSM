package com.iitsm.domain.incident.state;

import static com.iitsm.domain.incident.IncidentStatus.*;

final class NewState extends AbstractIncidentState {
    NewState() {
        super(NEW, CLASSIFIED, CANCELLED, DUPLICATE);
    }
}
