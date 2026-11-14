// com/iitsm/observer/IncidentEventPublisher.java
public void publish(IncidentEvent event) {
    Objects.requireNonNull(event, "event");
    for (IncidentObserver observer : observers) {
        observer.onEvent(event);
    }
}
