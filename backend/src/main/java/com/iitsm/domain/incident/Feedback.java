package com.iitsm.domain.incident;

import com.iitsm.domain.common.CreatedOnlyEntity;
import com.iitsm.exception.InvalidInputException;
import jakarta.persistence.*;

/** The reporter's rating (1 to 5) of how an incident was handled. Each incident has at most one. */
@Entity
@Table(name = "feedback")
public class Feedback extends CreatedOnlyEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false, unique = true)
    private Incident incident;

    @Column(nullable = false)
    private short rating;

    @Column
    private String comments;

    protected Feedback() { }

    public Feedback(Incident incident, short rating, String comments) {
        if (rating < 1 || rating > 5) {
            throw new InvalidInputException("Rating must be between 1 and 5");
        }
        this.incident = incident;
        this.rating = rating;
        this.comments = comments;
    }

    public Incident getIncident() { return incident; }
    public short getRating() { return rating; }
    public String getComments() { return comments; }
}
