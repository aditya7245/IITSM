package com.iitsm.observer;

/** Everything noteworthy that can happen to an incident. Listeners decide which of these they care about. */
public enum IncidentEventType {
    CREATED("Incident created"),
    CLASSIFIED("Incident classified"),
    PRIORITY_CHANGED("Priority changed"),
    ASSIGNED("Incident assigned"),
    ASSIGNMENT_FAILED("No technician available"),
    ACKNOWLEDGED("Incident acknowledged"),
    INVESTIGATION_STARTED("Investigation started"),
    INFO_REQUESTED("More information requested"),
    INFO_RECEIVED("Information received"),
    RESOLVED("Incident resolved"),
    REOPENED("Incident reopened"),
    VERIFIED("Resolution verified"),
    CLOSED("Incident closed"),
    CANCELLED("Incident cancelled"),
    MARKED_DUPLICATE("Marked as duplicate"),
    SLA_WARNING("SLA deadline approaching"),
    SLA_VIOLATED("SLA violated"),
    ESCALATED("Incident escalated");

    private final String title;

    IncidentEventType(String title) {
        this.title = title;
    }

    /** A short title suitable for an email subject or a notification heading. */
    public String title() {
        return title;
    }
}
