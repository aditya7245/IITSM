package com.iitsm.controller;

import com.iitsm.dto.AssistantDtos;
import com.iitsm.service.AssistantFacade;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The AI guide in the side panel. Any logged-in user may use it. */
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final AssistantFacade assistant;

    public AssistantController(AssistantFacade assistant) {
        this.assistant = assistant;
    }

    /** {@code enabled} is false when no AI provider is configured; the frontend then hides the guide. */
    @GetMapping("/status")
    public AssistantDtos.StatusResponse status() {
        return assistant.status();
    }

    /** Sends the conversation and returns the guide's reply. 503 when the guide is off or its provider fails. */
    @PostMapping("/chat")
    public AssistantDtos.ChatResponse chat(@Valid @RequestBody AssistantDtos.ChatRequest request) {
        return assistant.chat(request);
    }
}
