package com.iitsm.assistant;

import java.util.Objects;

/**
 * One message of a conversation with the AI guide.
 *
 * @param role    {@code user} (the person asking) or {@code assistant} (an earlier reply)
 * @param content the text
 */
public record ChatMessage(String role, String content) {

    public static final String USER = "user";
    public static final String ASSISTANT = "assistant";

    public ChatMessage {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(content, "content");
    }
}
