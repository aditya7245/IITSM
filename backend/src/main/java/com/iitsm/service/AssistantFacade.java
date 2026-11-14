package com.iitsm.service;

import com.iitsm.assistant.AssistantPromptBuilder;
import com.iitsm.assistant.ChatMessage;
import com.iitsm.assistant.LlmClient;
import com.iitsm.config.AssistantProperties;
import com.iitsm.domain.user.User;
import com.iitsm.dto.AssistantDtos;
import com.iitsm.dto.IncidentDetailResponse;
import com.iitsm.exception.AssistantUnavailableException;
import com.iitsm.exception.IitsmException;
import com.iitsm.exception.InvalidInputException;
import com.iitsm.security.CurrentUserService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * The AI guide: answers questions about using I-ITSM, aware of who is asking and what is on their screen.
 * Deliberately not {@code @Transactional}: no database connection is held while waiting for the AI provider.
 */
@Service
public class AssistantFacade {

    static final String GUIDE_RESOURCE = "assistant/app-guide.md";

    private final AssistantProperties props;
    private final CurrentUserService currentUser;
    private final IncidentFacade incidents;
    private final LlmClient llm;
    private final Clock clock;
    private final AssistantPromptBuilder prompts;

    public AssistantFacade(AssistantProperties props, CurrentUserService currentUser, IncidentFacade incidents,
                           LlmClient llm, Clock clock) {
        this.props = props;
        this.currentUser = currentUser;
        this.incidents = incidents;
        this.llm = llm;
        this.clock = clock;
        this.prompts = new AssistantPromptBuilder(loadGuide());
    }

    public AssistantDtos.StatusResponse status() {
        return new AssistantDtos.StatusResponse(props.enabled());
    }

    public AssistantDtos.ChatResponse chat(AssistantDtos.ChatRequest request) {
        User user = currentUser.requireUser();
        if (!props.enabled()) {
            throw new AssistantUnavailableException("The AI guide is not configured on this server");
        }
        List<AssistantDtos.Turn> turns = request.messages();
        if (!ChatMessage.USER.equals(turns.get(turns.size() - 1).role())) {
            throw new InvalidInputException("The last message must be the user's question");
        }

        String prompt = prompts.build(new AssistantPromptBuilder.Asker(user.getId(), user.getFullName(), user.getRole()),
                request.page(), visibleIncident(request.incidentId()), clock.instant());
        return new AssistantDtos.ChatResponse(llm.complete(prompt, recent(turns)));
    }

    /** The incident's details, or null when there is none or this user may not see it (nothing is revealed). */
    private IncidentDetailResponse visibleIncident(Long incidentId) {
        if (incidentId == null) {
            return null;
        }
        try {
            return incidents.get(incidentId);
        } catch (IitsmException notFoundOrNotAllowed) {
            return null;
        }
    }

    /** The last {@code maxTurns} messages, starting with a user message as some providers require. */
    private List<ChatMessage> recent(List<AssistantDtos.Turn> turns) {
        int from = Math.max(0, turns.size() - props.maxTurns());
        while (from < turns.size() - 1 && !ChatMessage.USER.equals(turns.get(from).role())) {
            from++;
        }
        List<ChatMessage> out = new ArrayList<>();
        for (AssistantDtos.Turn t : turns.subList(from, turns.size())) {
            out.add(new ChatMessage(t.role(), t.content()));
        }
        return out;
    }

    private static String loadGuide() {
        try (var in = new ClassPathResource(GUIDE_RESOURCE).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + GUIDE_RESOURCE, e);
        }
    }
}
