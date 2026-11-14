package com.iitsm.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.iitsm.config.AssistantProperties;
import com.iitsm.exception.AssistantUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Talks to any provider that offers the OpenAI-style {@code POST <base-url>/chat/completions} endpoint, which
 * covers Groq, Google Gemini, OpenRouter, Ollama, OpenAI and many others. Which one is used is pure configuration
 * ({@link AssistantProperties}); the API key stays on the server and is never sent to the browser.
 */
@Component
public class OpenAiCompatibleLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleLlmClient.class);
    private static final int LOGGED_BODY_CHARS = 300;

    private final AssistantProperties props;
    private final RestClient http;

    public OpenAiCompatibleLlmClient(AssistantProperties props) {
        this.props = props;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(props.timeoutSeconds()));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String complete(String systemPrompt, List<ChatMessage> conversation) {
        if (!props.enabled()) {
            throw new AssistantUnavailableException("The AI guide is not configured on this server");
        }
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        conversation.forEach(m -> messages.add(Map.of("role", m.role(), "content", m.content())));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.model());
        body.put("messages", messages);
        body.put("max_tokens", props.maxTokens());
        body.put("temperature", 0.3);

        JsonNode response;
        try {
            response = http.post()
                    .uri(endpoint())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(h -> {
                        if (!props.apiKey().isEmpty()) {
                            h.set(HttpHeaders.AUTHORIZATION, "Bearer " + props.apiKey());
                        }
                    })
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            log.warn("AI provider returned HTTP {}: {}", e.getStatusCode().value(), shorten(e.getResponseBodyAsString()));
            throw new AssistantUnavailableException(messageFor(e.getStatusCode().value()));
        } catch (ResourceAccessException e) {
            log.warn("AI provider could not be reached: {}", e.getMessage());
            throw new AssistantUnavailableException("The AI guide did not answer in time. Please try again.");
        } catch (RestClientException e) {
            if (NestedExceptionUtils.getRootCause(e) instanceof SocketTimeoutException) {
                // a timeout while reading the reply arrives wrapped differently from one while connecting
                log.warn("AI provider did not finish its reply within {} s", props.timeoutSeconds());
                throw new AssistantUnavailableException("The AI guide did not answer in time. Please try again.");
            }
            log.warn("AI provider call failed", e);
            throw new AssistantUnavailableException("The AI guide is unavailable right now. Please try again.");
        }

        JsonNode choice = response == null ? null : response.path("choices").path(0);
        String reply = choice == null ? null : choice.path("message").path("content").asText(null);
        if (reply == null || reply.isBlank()) {
            log.warn("AI provider sent no reply text: {}", response == null ? "empty body" : shorten(response.toString()));
            throw new AssistantUnavailableException("The AI guide gave an empty answer. Please try again.");
        }
        if ("length".equals(choice.path("finish_reason").asText())) {
            log.warn("AI reply was cut off at the max-tokens limit ({})", props.maxTokens());
            return reply.strip() + " … (answer cut short; ask for the rest)";
        }
        return reply.strip();
    }

    private String endpoint() {
        String base = props.baseUrl();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/chat/completions";
    }

    private static String messageFor(int status) {
        return switch (status) {
            case 401, 403 -> "The AI provider rejected the server's API key. Ask the administrator to check it.";
            case 404 -> "The AI provider does not know the configured model or address. Ask the administrator to check it.";
            case 429 -> "The AI guide has reached its usage limit for now. Please try again in a minute.";
            default -> "The AI guide is unavailable right now. Please try again.";
        };
    }

    private static String shorten(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= LOGGED_BODY_CHARS ? text : text.substring(0, LOGGED_BODY_CHARS) + "...";
    }
}
