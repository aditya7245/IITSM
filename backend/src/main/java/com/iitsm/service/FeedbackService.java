package com.iitsm.service;

import com.iitsm.domain.incident.Feedback;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentStatus;
import com.iitsm.domain.user.User;
import com.iitsm.exception.IncidentNotFoundException;
import com.iitsm.exception.InvalidInputException;
import com.iitsm.exception.InvalidStateTransitionException;
import com.iitsm.observer.IncidentEvent;
import com.iitsm.observer.IncidentEventPublisher;
import com.iitsm.observer.IncidentEventType;
import com.iitsm.repository.FeedbackRepository;
import com.iitsm.repository.IncidentRepository;
import com.iitsm.security.IncidentAccessPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * The last step of an incident's life: the reporter rates how it was handled, and the incident closes by itself.
 * Same pattern as {@link IncidentService}: load (404), check who is asking (403), apply, save, announce.
 */
@Service
public class FeedbackService {

    private final IncidentRepository incidents;
    private final FeedbackRepository feedbacks;
    private final IncidentEventPublisher events;
    private final IncidentAccessPolicy access;
    private final Clock clock;

    public FeedbackService(IncidentRepository incidents, FeedbackRepository feedbacks,
                           IncidentEventPublisher events, IncidentAccessPolicy access, Clock clock) {
        this.incidents = incidents;
        this.feedbacks = feedbacks;
        this.events = events;
        this.access = access;
        this.clock = clock;
    }

    /**
     * The reporter rates the handling of a verified incident, from 1 (poor) to 5 (excellent), with an optional comment.
     * The feedback is saved and the system then closes the incident (VERIFIED to CLOSED, no acting user) and
     * announces CLOSED. If anything is refused, nothing is saved and the incident stays VERIFIED.
     *
     * @throws InvalidInputException           if the rating is not 1 to 5, or feedback was already given for this incident
     * @throws InvalidStateTransitionException if the incident is not VERIFIED
     */
    @Transactional
    public Feedback submitFeedback(Long incidentId, int rating, String comments, User actor) {
        Incident incident = incidents.findById(incidentId)
                .orElseThrow(() -> new IncidentNotFoundException(String.valueOf(incidentId)));
        access.requireReporter(actor, incident);

        if (rating < 1 || rating > 5) {                      // checked as an int, so a huge number cannot wrap around into range
            throw new InvalidInputException("Rating must be between 1 and 5");
        }
        // Checked before the status: once feedback exists the incident is CLOSED, and "already given" is the clearer answer.
        if (feedbacks.existsByIncidentId(incidentId)) {
            throw new InvalidInputException("Feedback has already been submitted for this incident");
        }
        if (incident.getStatus() != IncidentStatus.VERIFIED) {
            throw new InvalidStateTransitionException(
                    "Feedback can only be given once the fix is verified (this incident is " + incident.getStatus() + ")");
        }

        Instant now = clock.instant();
        Feedback feedback = new Feedback(incident, (short) rating, cleaned(comments));
        incident.close(now);

        feedbacks.save(feedback);
        incidents.save(incident);
        events.publish(IncidentEvent.of(IncidentEventType.CLOSED, incident, null, IncidentStatus.VERIFIED,
                incident.getStatus(), "Closed automatically after feedback (rating " + rating + " of 5)", now));
        return feedback;
    }

    /** Blank comments are stored as "no comment". */
    private static String cleaned(String comments) {
        return comments == null || comments.isBlank() ? null : comments.strip();
    }
}
