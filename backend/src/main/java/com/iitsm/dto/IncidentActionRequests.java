package com.iitsm.dto;

import com.iitsm.domain.incident.CommentType;
import com.iitsm.domain.incident.Priority;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** The small request bodies of the actions on an incident, kept together because each is only a field or two. */
public final class IncidentActionRequests {

    private IncidentActionRequests() { }

    /** The text of a possible incident, used to suggest a category before it is reported. */
    public record SuggestCategory(String title, String description) { }

    public record Resolve(@NotBlank String resolution) { }

    public record Reopen(@NotBlank String reason) { }

    public record MarkDuplicate(@NotNull Long originalId) { }

    public record Reassign(@NotNull Long technicianId) { }

    public record ChangePriority(@NotNull Priority priority) { }

    /** NOTE is an internal note, INFO_REQUEST asks the reporter for details, INFO_RESPONSE is the reporter's answer. */
    public record AddComment(@NotNull CommentType type, @NotBlank String body) { }

    public record SubmitFeedback(@Min(1) @Max(5) int rating, String comments) { }
}
