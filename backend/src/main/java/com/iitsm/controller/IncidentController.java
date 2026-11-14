package com.iitsm.controller;

import com.iitsm.dto.AuditResponse;
import com.iitsm.dto.CategorySuggestionResponse;
import com.iitsm.dto.CommentResponse;
import com.iitsm.dto.CreateIncidentRequest;
import com.iitsm.dto.CreateIncidentResponse;
import com.iitsm.dto.FeedbackResponse;
import com.iitsm.dto.IncidentActionRequests;
import com.iitsm.dto.IncidentDetailResponse;
import com.iitsm.dto.TechnicianSummaryResponse;
import com.iitsm.service.DuplicateCandidate;
import com.iitsm.service.IncidentFacade;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The web addresses for reporting and working on incidents. The controller only receives and returns plain
 * request and response objects; everything else is done by {@link IncidentFacade}. Which role may call an address is
 * declared here; whether this particular person may touch this particular incident (for example only its reporter
 * may verify it) is checked by the service layer, and a refusal there is also a 403.
 */
@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentFacade incidents;

    public IncidentController(IncidentFacade incidents) {
        this.incidents = incidents;
    }

    // ------------------------------------------------------------------ reporting and reading

    /** Any logged-in user can report an incident. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateIncidentResponse create(@Valid @RequestBody CreateIncidentRequest request) {
        return incidents.create(request);
    }

    @GetMapping("/{id}")
    public IncidentDetailResponse get(@PathVariable Long id) {
        return incidents.get(id);
    }

    /** The three best categories for what the user has typed so far. */
    @PostMapping("/suggest-category")
    public List<CategorySuggestionResponse> suggestCategory(@RequestBody IncidentActionRequests.SuggestCategory request) {
        return incidents.suggestCategory(request);
    }

    // ------------------------------------------------------------------ the people working on it

    @PostMapping("/{id}/acknowledge")
    public IncidentDetailResponse acknowledge(@PathVariable Long id) {
        return incidents.acknowledge(id);
    }

    @PostMapping("/{id}/start")
    public IncidentDetailResponse start(@PathVariable Long id) {
        return incidents.startProgress(id);
    }

    @PostMapping("/{id}/resolve")
    public IncidentDetailResponse resolve(@PathVariable Long id, @Valid @RequestBody IncidentActionRequests.Resolve request) {
        return incidents.resolve(id, request.resolution());
    }

    @PostMapping("/{id}/verify")
    public IncidentDetailResponse verify(@PathVariable Long id) {
        return incidents.verify(id);
    }

    @PostMapping("/{id}/reopen")
    public IncidentDetailResponse reopen(@PathVariable Long id, @Valid @RequestBody IncidentActionRequests.Reopen request) {
        return incidents.reopen(id, request.reason());
    }

    @PostMapping("/{id}/cancel")
    public IncidentDetailResponse cancel(@PathVariable Long id) {
        return incidents.cancel(id);
    }

    // ------------------------------------------------------------------ administrators only

    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('ADMIN')")
    public IncidentDetailResponse close(@PathVariable Long id) {
        return incidents.close(id);
    }

    @PostMapping("/{id}/duplicate")
    @PreAuthorize("hasRole('ADMIN')")
    public IncidentDetailResponse markDuplicate(@PathVariable Long id, @Valid @RequestBody IncidentActionRequests.MarkDuplicate request) {
        return incidents.markDuplicate(id, request.originalId());
    }

    @PostMapping("/{id}/reassign")
    @PreAuthorize("hasRole('ADMIN')")
    public IncidentDetailResponse reassign(@PathVariable Long id, @Valid @RequestBody IncidentActionRequests.Reassign request) {
        return incidents.reassign(id, request.technicianId());
    }

    @PostMapping("/{id}/priority")
    @PreAuthorize("hasRole('ADMIN')")
    public IncidentDetailResponse changePriority(@PathVariable Long id, @Valid @RequestBody IncidentActionRequests.ChangePriority request) {
        return incidents.changePriority(id, request.priority());
    }

    /** The three best technicians to take the incident. */
    @GetMapping("/{id}/recommendations")
    @PreAuthorize("hasRole('ADMIN')")
    public List<TechnicianSummaryResponse> recommendations(@PathVariable Long id) {
        return incidents.recommendations(id);
    }

    /** Open incidents on the same service that look like the same problem, for marking duplicates. */
    @GetMapping("/{id}/similar")
    @PreAuthorize("hasRole('ADMIN')")
    public List<DuplicateCandidate> similar(@PathVariable Long id) {
        return incidents.similar(id);
    }

    // ------------------------------------------------------------------ conversation, feedback, history

    @GetMapping("/{id}/comments")
    public List<CommentResponse> comments(@PathVariable Long id) {
        return incidents.comments(id);
    }

    /** The {@code type} in the body chooses what happens: an internal note, a request for information, or an answer. */
    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse addComment(@PathVariable Long id, @Valid @RequestBody IncidentActionRequests.AddComment request) {
        return incidents.addComment(id, request);
    }

    /** The reporter's rating of the handling; this also closes the incident. */
    @PostMapping("/{id}/feedback")
    @ResponseStatus(HttpStatus.CREATED)
    public FeedbackResponse submitFeedback(@PathVariable Long id, @Valid @RequestBody IncidentActionRequests.SubmitFeedback request) {
        return incidents.submitFeedback(id, request);
    }

    @GetMapping("/{id}/feedback")
    public FeedbackResponse feedback(@PathVariable Long id) {
        return incidents.feedback(id);
    }

    @GetMapping("/{id}/history")
    public List<AuditResponse> history(@PathVariable Long id) {
        return incidents.history(id);
    }
}
