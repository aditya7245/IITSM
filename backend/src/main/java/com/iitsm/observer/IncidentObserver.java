package com.iitsm.observer;

/**
 * Observer pattern: something that wants to know when an incident changes, such as the audit log or the
 * notification sender. Add a new observer by writing a class that implements this interface and registering it
 * as a Spring component; the code that changes incidents does not need to know about it.
 * Use {@code @Order} to decide who runs first.
 */
public interface IncidentObserver {

    void onEvent(IncidentEvent event);
}
