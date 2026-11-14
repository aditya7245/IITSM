package com.iitsm.service;

import com.iitsm.domain.incident.CommentType;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentComment;
import com.iitsm.domain.incident.IncidentStatus;
import com.iitsm.domain.user.User;
import com.iitsm.exception.IncidentNotFoundException;
import com.iitsm.exception.InvalidStateTransitionException;
import com.iitsm.observer.IncidentEvent;
import com.iitsm.observer.IncidentEventPublisher;
import com.iitsm.observer.IncidentEventType;
import com.iitsm.repository.IncidentCommentRepository;
import com.iitsm.repository.IncidentRepository;
import com.iitsm.security.IncidentAccessPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Set;

/**
 * The conversation on an incident: internal notes, the technician's request for more information and the
 * reporter's answer. Asking for information pauses the SLA clock; the answer starts it again.
 * Same pattern as {@link IncidentService}: load (404), check who is asking (403), apply, save, announce.
 * Comments are only possible while the incident is not finished (not CLOSED, CANCELLED or DUPLICATE).
 */
@Service
public class IncidentCommentService {

    /** The only comment types the reporter may see; internal notes are for staff. */
    private static final Set<CommentType> VISIBLE_TO_REPORTER = Set.of(CommentType.INFO_REQUEST, CommentType.INFO_RESPONSE);

    private final IncidentRepository incidents;
    private final IncidentCommentRepository comments;
    private final IncidentEventPublisher events;
    private final IncidentAccessPolicy access;
    private final Clock clock;

    public IncidentCommentService(IncidentRepository incidents, IncidentCommentRepository comments,
                                  IncidentEventPublisher events, IncidentAccessPolicy access, Clock clock) {
        this.incidents = incidents;
        this.comments = comments;
        this.events = events;
        this.access = access;
        this.clock = clock;
    }

    /**
     * An internal note, visible to staff only. Only the assignee or an admin may add one.
     * Nothing is announced: a note does not change the incident.
     */
    @Transactional
    public IncidentComment addNote(Long incidentId, String body, User actor) {
        Incident incident = load(incidentId);
        access.requireNoteAuthor(actor, incident);
        requireNotFinished(incident);
        return comments.save(new IncidentComment(incident, actor, CommentType.NOTE, body));
    }

    /**
     * The assignee asks the reporter for more information. IN_PROGRESS to PENDING_INFO, and the SLA clock pauses.
     * Announces INFO_REQUESTED.
     */
    @Transactional
    public IncidentComment requestInfo(Long incidentId, String body, User actor) {
        Incident incident = load(incidentId);
        access.requireAssignee(actor, incident);
        IncidentComment comment = new IncidentComment(incident, actor, CommentType.INFO_REQUEST, body);  // refuses a blank text
        IncidentStatus previous = incident.getStatus();

        incident.requestInfo(clock.instant());

        incidents.save(incident);
        comments.save(comment);
        publish(IncidentEventType.INFO_REQUESTED, incident, actor, previous,
                "More information requested by " + actor.getFullName() + ": " + IncidentService.shorten(body));
        return comment;
    }

    /**
     * The reporter answers. If the incident was waiting for information (PENDING_INFO) it goes back to
     * IN_PROGRESS, the SLA clock resumes with both deadlines moved later by the waiting time, and INFO_RECEIVED
     * is announced. In any other unfinished status the answer is only saved as a comment.
     */
    @Transactional
    public IncidentComment respondToInfo(Long incidentId, String body, User actor) {
        Incident incident = load(incidentId);
        access.requireReporter(actor, incident);
        requireNotFinished(incident);
        IncidentComment comment = new IncidentComment(incident, actor, CommentType.INFO_RESPONSE, body);  // refuses a blank text
        IncidentStatus previous = incident.getStatus();

        if (previous == IncidentStatus.PENDING_INFO) {
            incident.receiveInfo(clock.instant());
            incidents.save(incident);
            comments.save(comment);
            publish(IncidentEventType.INFO_RECEIVED, incident, actor, previous,
                    "Information received from " + actor.getFullName() + ": " + IncidentService.shorten(body));
        } else {
            comments.save(comment);
        }
        return comment;
    }

    /**
     * The thread, oldest first. Anyone who may view the incident may read it, but the reporter only sees the
     * information requests and answers; internal notes are hidden from them.
     */
    @Transactional(readOnly = true)
    public List<IncidentComment> listComments(Long incidentId, User actor) {
        Incident incident = load(incidentId);
        access.requireView(actor, incident);
        List<IncidentComment> thread = comments.findByIncidentIdOrderByCreatedAtAscIdAsc(incidentId);
        if (isReporter(actor, incident)) {
            return thread.stream().filter(comment -> VISIBLE_TO_REPORTER.contains(comment.getType())).toList();
        }
        return thread;
    }

    // ------------------------------------------------------------------ helpers

    private Incident load(Long id) {
        return incidents.findById(id).orElseThrow(() -> new IncidentNotFoundException(String.valueOf(id)));
    }

    private static void requireNotFinished(Incident incident) {
        if (!incident.getStatus().isOpen()) {
            throw new InvalidStateTransitionException("Comments cannot be added to a " + incident.getStatus() + " incident");
        }
    }

    private static boolean isReporter(User user, Incident incident) {
        return incident.getReporter() != null && user.getId() != null && user.getId().equals(incident.getReporter().getId());
    }

    private void publish(IncidentEventType type, Incident incident, User actor, IncidentStatus previous, String description) {
        events.publish(IncidentEvent.of(type, incident, actor, previous, incident.getStatus(), description, clock.instant()));
    }
}
