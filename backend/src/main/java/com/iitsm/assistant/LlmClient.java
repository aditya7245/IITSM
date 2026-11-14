package com.iitsm.assistant;

import java.util.List;

/**
 * A large language model that can continue a conversation. The rest of the application depends only on this
 * interface, so the provider can be replaced (or faked in tests) without touching the assistant logic.
 */
public interface LlmClient {

    /**
     * @param systemPrompt the instructions and background the model must follow
     * @param conversation the messages so far, oldest first, ending with the user's new question
     * @return the model's reply
     * @throws com.iitsm.exception.AssistantUnavailableException if the provider is not configured, fails or times out
     */
    String complete(String systemPrompt, List<ChatMessage> conversation);
}
