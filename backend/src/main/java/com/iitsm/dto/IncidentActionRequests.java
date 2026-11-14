package com.iitsm.dto;

import com.iitsm.domain.incident.CommentType;
import com.iitsm.domain.incident.Priority;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The small request bodies of the actions on an incident, kept together because each is only a field or two. */
public final class IncidentActionRequests {

    /** Longest free text accepted in one field (a description, resolution, comment or reason). */
    public static final int MAX_TEXT = 5000;

    private IncidentActionRequests() { }

    /** The text of a possible incident, used to suggest a category before it is reported. */
    public record SuggestCategory(@Size(max = 200) String title, @Size(max = MAX_TEXT) String description) { }

    public record Resolve(@NotBlank @Size(max = MAX_TEXT) String resolution) { }

    public record Reopen(@NotBlank @Size(max = MAX_TEXT) String reason) { }

    public record MarkDuplicate(@NotNull Long originalId) { }

    public record Reassign(@NotNull Long technicianId) { }

    public record ChangePriority(@NotNull Priority priority) { }

    /** NOTE is an internal note, INFO_REQUEST asks the reporter for details, INFO_RESPONSE is the reporter's answer. */
    public record AddComment(@NotNull CommentType type, @NotBlank @Size(max = MAX_TEXT) String body) { }

    public record SubmitFeedback(@Min(1) @Max(5) int rating, @Size(max = 2000) String comments) { }
}
