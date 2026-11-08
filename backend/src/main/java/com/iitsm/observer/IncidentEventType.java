package com.iitsm.observer;

/** Everything noteworthy that can happen to an incident. Listeners decide which of these they care about. */
public enum IncidentEventType {
    CREATED,
    CLASSIFIED,
    PRIORITY_CHANGED,
    ASSIGNED,
    ASSIGNMENT_FAILED,
    ACKNOWLEDGED,
    INVESTIGATION_STARTED,
    INFO_REQUESTED,
    INFO_RECEIVED,
    RESOLVED,
    REOPENED,
    VERIFIED,
    CLOSED,
    CANCELLED,
    MARKED_DUPLICATE,
    SLA_WARNING,
    SLA_VIOLATED,
    ESCALATED
}
