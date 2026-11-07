package com.iitsm.domain.incident;

import com.iitsm.domain.common.CreatedOnlyEntity;
import com.iitsm.domain.user.User;
import com.iitsm.exception.InvalidInputException;
import jakarta.persistence.*;

/** One entry on an incident's thread: an internal note, a request for information, or the reporter's answer. */
@Entity
@Table(name = "incident_comments")
public class IncidentComment extends CreatedOnlyEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommentType type;

    @Column(nullable = false)
    private String body;

    protected IncidentComment() { }

    public IncidentComment(Incident incident, User author, CommentType type, String body) {
        if (body == null || body.isBlank()) {
            throw new InvalidInputException("Comment text must not be blank");
        }
        this.incident = incident;
        this.author = author;
        this.type = type;
        this.body = body;
    }

    public Incident getIncident() { return incident; }
    public User getAuthor() { return author; }
    public CommentType getType() { return type; }
    public String getBody() { return body; }
}
