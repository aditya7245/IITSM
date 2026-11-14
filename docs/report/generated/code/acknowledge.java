// com/iitsm/domain/incident/Incident.java
public void acknowledge(Instant now) {
    requireMoveTo(IncidentStatus.ACKNOWLEDGED);
    this.status = IncidentStatus.ACKNOWLEDGED;
    this.respondedAt = now;
}
