package com.iitsm.observer;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Tells every observer about an event, one after another, in {@code @Order} order, on the caller's thread.
 * If an observer throws, the exception reaches the caller and the remaining observers are not called.
 * (Observers that must never break the caller, such as notifications, catch their own exceptions.)
 */
@Component
public class IncidentEventPublisher {

    private final List<IncidentObserver> observers;

    /** Used by Spring. Works even when no observer exists yet, which plain list injection would not. */
    @Autowired
    public IncidentEventPublisher(ObjectProvider<IncidentObserver> provider) {
        this(provider.orderedStream().toList());
    }

    /** Used directly in tests. The list is sorted by {@code @Order} here, so the result does not depend on how it was built. */
    public IncidentEventPublisher(List<IncidentObserver> observers) {
        List<IncidentObserver> sorted = new ArrayList<>(observers);
        AnnotationAwareOrderComparator.sort(sorted);
        this.observers = List.copyOf(sorted);
    }

    public void publish(IncidentEvent event) {
        Objects.requireNonNull(event, "event");
        for (IncidentObserver observer : observers) {
            observer.onEvent(event);
        }
    }
}
