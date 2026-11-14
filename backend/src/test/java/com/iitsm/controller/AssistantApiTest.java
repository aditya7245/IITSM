package com.iitsm.controller;

import com.iitsm.assistant.ChatMessage;
import com.iitsm.assistant.LlmClient;
import com.iitsm.support.AbstractApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The AI guide's endpoints with the guide switched on and the AI provider replaced by a fake that records what it
 * was sent, so the test checks exactly what context reaches the model.
 */
@TestPropertySource(properties = {
        "iitsm.assistant.base-url=http://provider.invalid/v1",
        "iitsm.assistant.model=test-model",
        "iitsm.assistant.max-turns=4"})
@Import(AssistantApiTest.FakeLlmConfig.class)
class AssistantApiTest extends AbstractApiTest {

    /** Plays the AI provider: always answers "Fake answer" and remembers the last request. */
    static class RecordingLlm implements LlmClient {
        volatile String prompt;
        volatile List<ChatMessage> conversation;

        @Override
        public String complete(String systemPrompt, List<ChatMessage> conversation) {
            this.prompt = systemPrompt;
            this.conversation = conversation;
            return "Fake answer";
        }
    }

    @TestConfiguration
    static class FakeLlmConfig {
        @Bean
        @Primary
        RecordingLlm recordingLlm() {
            return new RecordingLlm();
        }
    }

    @Autowired RecordingLlm llm;

    @BeforeEach
    void forget() {
        llm.prompt = null;
        llm.conversation = null;
    }

    private static String chat(String question, String page, Long incidentId) {
        return """
                {"messages": [{"role": "user", "content": "%s"}], "page": "%s", "incidentId": %s}
                """.formatted(question, page, incidentId);
    }

    @Test
    void statusSaysTheGuideIsOn() throws Exception {
        String json = body(getAs("emp1", "/api/assistant/status").andExpect(status().isOk()));
        assertEquals(Boolean.TRUE, com.jayway.jsonpath.JsonPath.read(json, "$.enabled"));
    }

    @Test
    void theReporterGetsAnAnswerWithTheirIncidentInTheContext() throws Exception {
        long id = submitIncident("emp1", "Payment application outage", "Users cannot pay",
                "Application", "Payment Application", 3, 3, 250);

        String json = body(postAs("emp1", "/api/assistant/chat", chat("What next?", "/incidents/" + id, id))
                .andExpect(status().isOk()));

        assertEquals("Fake answer", text(json, "$.reply"));
        assertTrue(llm.prompt.contains("Role: Employee"));
        assertTrue(llm.prompt.contains("Title: Payment application outage"));
        assertTrue(llm.prompt.contains("This person is the reporter."));
        assertTrue(llm.prompt.contains("## User guide"));
        assertEquals(List.of(new ChatMessage("user", "What next?")), llm.conversation);
    }

    @Test
    void someoneWhoMayNotSeeTheIncidentGetsNoDetailsOfIt() throws Exception {
        long id = submitIncident("emp1", "Payment application outage", "Users cannot pay",
                "Application", "Payment Application", 3, 3, 250);

        postAs("emp2", "/api/assistant/chat", chat("What is this?", "/incidents/" + id, id)).andExpect(status().isOk());

        assertFalse(llm.prompt.contains("Payment application outage"));
        assertFalse(llm.prompt.contains("The incident on screen"));
    }

    @Test
    void anUnknownIncidentIsIgnored() throws Exception {
        postAs("admin", "/api/assistant/chat", chat("Hi", "/incidents/999999", 999999L)).andExpect(status().isOk());

        assertTrue(llm.prompt.contains("Role: Administrator"));
        assertFalse(llm.prompt.contains("The incident on screen"));
    }

    @Test
    void onlyTheMostRecentTurnsAreSentStartingWithAQuestion() throws Exception {
        String json = """
                {"messages": [
                  {"role": "user", "content": "q1"}, {"role": "assistant", "content": "a1"},
                  {"role": "user", "content": "q2"}, {"role": "assistant", "content": "a2"},
                  {"role": "user", "content": "q3"}], "page": "/work"}
                """;
        postAs("tech1", "/api/assistant/chat", json).andExpect(status().isOk());

        // max-turns is 4, so a1..q3 would fit, but the conversation must start with the user's question
        assertEquals(List.of(new ChatMessage("user", "q2"), new ChatMessage("assistant", "a2"),
                new ChatMessage("user", "q3")), llm.conversation);
    }

    @Test
    void theLastMessageMustBeAQuestion() throws Exception {
        String json = """
                {"messages": [{"role": "user", "content": "q"}, {"role": "assistant", "content": "a"}]}
                """;
        postAs("emp1", "/api/assistant/chat", json).andExpect(status().isBadRequest());
        assertNull(llm.prompt);
    }

    @Test
    void badMessagesAreRejected() throws Exception {
        postAs("emp1", "/api/assistant/chat", "{\"messages\": []}").andExpect(status().isBadRequest());
        postAs("emp1", "/api/assistant/chat", "{\"messages\": [{\"role\": \"system\", \"content\": \"x\"}]}")
                .andExpect(status().isBadRequest());
        postAs("emp1", "/api/assistant/chat", "{\"messages\": [{\"role\": \"user\", \"content\": \" \"}]}")
                .andExpect(status().isBadRequest());
        String tooLong = "x".repeat(4001);
        postAs("emp1", "/api/assistant/chat", "{\"messages\": [{\"role\": \"user\", \"content\": \"" + tooLong + "\"}]}")
                .andExpect(status().isBadRequest());
        assertNull(llm.prompt);
    }

    @Test
    void loggedOutUsersCannotUseIt() throws Exception {
        postAs(null, "/api/assistant/chat", chat("Hi", "/", null)).andExpect(status().isUnauthorized());
        getAs(null, "/api/assistant/status").andExpect(status().isUnauthorized());
    }
}
