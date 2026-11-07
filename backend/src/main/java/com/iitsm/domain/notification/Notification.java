package com.iitsm.domain.notification;

import com.iitsm.domain.common.CreatedOnlyEntity;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.user.User;
import jakarta.persistence.*;

/** An in-app message for one user about something that happened to an incident. */
@Entity
@Table(name = "notifications")
public class Notification extends CreatedOnlyEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id")
    private Incident incident;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    protected Notification() { }

    public Notification(User user, Incident incident, String eventType, String message) {
        this.user = user;
        this.incident = incident;
        this.eventType = eventType;
        this.message = message;
    }

    public User getUser() { return user; }
    public Incident getIncident() { return incident; }
    public String getEventType() { return eventType; }
    public String getMessage() { return message; }
    public boolean isRead() { return read; }

    public void markRead() {
        this.read = true;
    }
}
