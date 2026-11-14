package com.iitsm.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Request and response bodies of the AI guide, kept together because each is only a field or two. */
public final class AssistantDtos {

    private AssistantDtos() { }

    /** One message of the conversation. {@code role} is {@code user} or {@code assistant}. */
    public record Turn(@NotNull @Pattern(regexp = "user|assistant") String role,
                       @NotBlank @Size(max = 4000) String content) { }

    /**
     * The conversation so far (oldest first, ending with the new question), the page the user is on, and the
     * incident on screen if any. The incident's details are loaded on the server, and only if the user may see it.
     */
    public record ChatRequest(@NotEmpty @Size(max = 40) List<@Valid @NotNull Turn> messages,
                              @Size(max = 200) String page,
                              Long incidentId) { }

    public record ChatResponse(String reply) { }

    /** Whether the AI guide is configured on this server; the frontend hides its button when it is not. */
    public record StatusResponse(boolean enabled) { }
}
