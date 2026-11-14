package com.iitsm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under {@code iitsm.assistant}: the AI guide's connection to a chat-completions API in the
 * OpenAI-compatible format (Groq, Gemini, OpenRouter, Ollama, OpenAI and others all accept it).
 * The assistant is switched on only when both {@code baseUrl} and {@code model} are set.
 *
 * @param baseUrl        the API root, for example {@code https://api.groq.com/openai/v1}; blank turns the assistant off
 * @param apiKey         sent as a Bearer token; may be blank for a local server such as Ollama
 * @param model          the model name the provider expects
 * @param maxTokens      the longest reply the model may write
 * @param timeoutSeconds how long to wait for the provider before giving up
 * @param maxTurns       how many recent messages of the conversation are sent with each question
 */
@ConfigurationProperties(prefix = "iitsm.assistant")
public record AssistantProperties(String baseUrl, String apiKey, String model, int maxTokens, int timeoutSeconds,
                                  int maxTurns) {

    public AssistantProperties {
        baseUrl = baseUrl == null ? "" : baseUrl.strip();
        apiKey = apiKey == null ? "" : apiKey.strip();
        model = model == null ? "" : model.strip();
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("iitsm.assistant.max-tokens must be greater than 0");
        }
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("iitsm.assistant.timeout-seconds must be greater than 0");
        }
        if (maxTurns <= 0) {
            throw new IllegalArgumentException("iitsm.assistant.max-turns must be greater than 0");
        }
    }

    /** True when a provider has been configured. */
    public boolean enabled() {
        return !baseUrl.isEmpty() && !model.isEmpty();
    }
}
