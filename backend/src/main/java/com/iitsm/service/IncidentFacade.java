package com.iitsm.service;

import com.iitsm.domain.catalog.Category;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentComment;
import com.iitsm.domain.incident.Priority;
import com.iitsm.domain.user.User;
import com.iitsm.dto.AuditResponse;
import com.iitsm.dto.CategorySuggestionResponse;
import com.iitsm.dto.CommentResponse;
import com.iitsm.dto.CreateIncidentRequest;
import com.iitsm.dto.CreateIncidentResponse;
import com.iitsm.dto.FeedbackResponse;
import com.iitsm.dto.IncidentActionRequests;
import com.iitsm.dto.IncidentDetailResponse;
import com.iitsm.dto.ResponseMapper;
import com.iitsm.dto.TechnicianSummaryResponse;
import com.iitsm.exception.IncidentNotFoundException;
import com.iitsm.exception.ResourceNotFoundException;
import com.iitsm.repository.CategoryRepository;
import com.iitsm.repository.FeedbackRepository;
import com.iitsm.repository.IncidentRepository;
import com.iitsm.security.CurrentUserService;
import com.iitsm.security.IncidentAccessPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * What the incident controller calls. Each method is one transaction that finds out who is asking, runs the
 * matching service, and turns the result into a response object before the transaction ends (the entities are
 * loaded lazily, so they can only be read while it is open). Controllers therefore never see an entity.
 * Who may do what is decided by the services and the access policy, not here.
 */
@Service
@Transactional
public class IncidentFacade {

    static final int SUGGESTIONS = 3;
    static final int RECOMMENDATIONS = 3;

    private final CurrentUserService currentUser;
    private final IncidentService incidentService;
    private final IncidentCommentService commentService;
    private final FeedbackService feedbackService;
    private final AssignmentService assignment;
    private final AuditService auditService;
    private final IncidentRepository incidents;
    private final FeedbackRepository feedbacks;
    private final CategoryRepository categories;
    private final CategorySuggester suggester;
    private final IncidentAccessPolicy access;
    private final ResponseMapper mapper;

    public IncidentFacade(CurrentUserService currentUser, IncidentService incidentService,
                          IncidentCommentService commentService, FeedbackService feedbackService,
                          AssignmentService assignment, AuditService auditService, IncidentRepository incidents,
                          FeedbackRepository feedbacks, CategoryRepository categories, CategorySuggester suggester,
                          IncidentAccessPolicy access, ResponseMapper mapper) {
        this.currentUser = currentUser;
        this.incidentService = incidentService;
        this.commentService = commentService;
        this.feedbackService = feedbackService;
        this.assignment = assignment;
        this.auditService = auditService;
        this.incidents = incidents;
        this.feedbacks = feedbacks;
        this.categories = categories;
        this.suggester = suggester;
        this.access = access;
        this.mapper = mapper;
    }

    // ------------------------------------------------------------------ reporting and reading

    public CreateIncidentResponse create(CreateIncidentRequest request) {
        User actor = currentUser.requireUser();
        CreateIncidentCommand command = new CreateIncidentCommand(request.title(), request.description(),
                request.categoryId(), request.serviceId(), request.impact(), request.urgency(), request.affectedUsers());
        return mapper.toCreateResponse(incidentService.create(command, actor));
    }

    public IncidentDetailResponse get(Long id) {
        return mapper.toDetail(viewable(id, currentUser.requireUser()));
    }

    /** The best matching categories for a text, at most {@value #SUGGESTIONS}, before the incident is reported. */
    public List<CategorySuggestionResponse> suggestCategory(IncidentActionRequests.SuggestCategory request) {
        currentUser.requireUser();
        String text = (request.title() == null ? "" : request.title()) + " "
                + (request.description() == null ? "" : request.description());
        List<Category> active = categories.findByActiveTrue();
        return suggester.suggest(text, active).stream().limit(SUGGESTIONS).map(mapper::toSuggestion).toList();
    }

    // ------------------------------------------------------------------ lifecycle

    public IncidentDetailResponse acknowledge(Long id) {
        return mapper.toDetail(incidentService.acknowledge(id, currentUser.requireUser()));
    }

    public IncidentDetailResponse startProgress(Long id) {
        return mapper.toDetail(incidentService.startProgress(id, currentUser.requireUser()));
    }

    public IncidentDetailResponse resolve(Long id, String resolution) {
        return mapper.toDetail(incidentService.resolve(id, resolution, currentUser.requireUser()));
    }

    public IncidentDetailResponse verify(Long id) {
        return mapper.toDetail(incidentService.verify(id, currentUser.requireUser()));
    }

    public IncidentDetailResponse reopen(Long id, String reason) {
        return mapper.toDetail(incidentService.reopen(id, reason, currentUser.requireUser()));
    }

    public IncidentDetailResponse cancel(Long id) {
        return mapper.toDetail(incidentService.cancel(id, currentUser.requireUser()));
    }

    // ------------------------------------------------------------------ administrators

    public IncidentDetailResponse close(Long id) {
        return mapper.toDetail(incidentService.close(id, currentUser.requireUser()));
    }

    public IncidentDetailResponse markDuplicate(Long id, Long originalId) {
        return mapper.toDetail(incidentService.markDuplicate(id, originalId, currentUser.requireUser()));
    }

    public IncidentDetailResponse reassign(Long id, Long technicianId) {
        return mapper.toDetail(incidentService.reassign(id, technicianId, currentUser.requireUser()));
    }

    public IncidentDetailResponse changePriority(Long id, Priority priority) {
        return mapper.toDetail(incidentService.changePriority(id, priority, currentUser.requireUser()));
    }

    /** The best technicians to take this incident, at most {@value #RECOMMENDATIONS}. */
    public List<TechnicianSummaryResponse> recommendations(Long id) {
        Incident incident = load(id);
        return assignment.recommend(incident, RECOMMENDATIONS).stream().map(mapper::toTechnician).toList();
    }

    // ------------------------------------------------------------------ comments, feedback, history

    public List<CommentResponse> comments(Long id) {
        return commentService.listComments(id, currentUser.requireUser()).stream().map(mapper::toComment).toList();
    }

    /** Sends the comment to the matching action: a note, a request for information, or the answer to one. */
    public CommentResponse addComment(Long id, IncidentActionRequests.AddComment request) {
        User actor = currentUser.requireUser();
        IncidentComment comment = switch (request.type()) {
            case NOTE -> commentService.addNote(id, request.body(), actor);
            case INFO_REQUEST -> commentService.requestInfo(id, request.body(), actor);
            case INFO_RESPONSE -> commentService.respondToInfo(id, request.body(), actor);
        };
        return mapper.toComment(comment);
    }

    public FeedbackResponse submitFeedback(Long id, IncidentActionRequests.SubmitFeedback request) {
        return mapper.toFeedback(feedbackService.submitFeedback(id, request.rating(), request.comments(),
                currentUser.requireUser()));
    }

    /** The feedback given for an incident, for anyone who may view it. */
    public FeedbackResponse feedback(Long id) {
        viewable(id, currentUser.requireUser());
        return feedbacks.findByIncidentId(id).map(mapper::toFeedback)
                .orElseThrow(() -> new ResourceNotFoundException("No feedback has been given for incident " + id));
    }

    public List<AuditResponse> history(Long id) {
        viewable(id, currentUser.requireUser());
        return auditService.history(id).stream().map(mapper::toAudit).toList();
    }

    // ------------------------------------------------------------------ helpers

    private Incident load(Long id) {
        Objects.requireNonNull(id, "id");
        return incidents.findById(id).orElseThrow(() -> new IncidentNotFoundException(String.valueOf(id)));
    }

    /** The incident, if the user may see it. */
    private Incident viewable(Long id, User user) {
        Incident incident = load(id);
        access.requireView(user, incident);
        return incident;
    }
}
