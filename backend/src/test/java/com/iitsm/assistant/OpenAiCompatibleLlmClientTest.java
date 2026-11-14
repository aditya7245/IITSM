package com.iitsm.assistant;

import com.iitsm.config.AssistantProperties;
import com.iitsm.exception.AssistantUnavailableException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** The client against a tiny local HTTP server that plays the AI provider. */
class OpenAiCompatibleLlmClientTest {

    HttpServer server;
    final AtomicReference<String> requestBody = new AtomicReference<>();
    final AtomicReference<String> authHeader = new AtomicReference<>();
    final AtomicReference<String> path = new AtomicReference<>();
    volatile int status = 200;
    volatile String responseBody = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"  Click Report.  \"}}]}";

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    OpenAiCompatibleLlmClient client(String apiKey) {
        String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/";
        return new OpenAiCompatibleLlmClient(new AssistantProperties(base, apiKey, "test-model", 100, 5, 10));
    }

    @Test
    void sendsTheSystemPromptAndConversationAndReturnsTheTrimmedReply() {
        String reply = client("secret-key").complete("SYSTEM", List.of(
                new ChatMessage("user", "hi"), new ChatMessage("assistant", "hello"), new ChatMessage("user", "how?")));

        assertEquals("Click Report.", reply);
        assertEquals("/v1/chat/completions", path.get());
        assertEquals("Bearer secret-key", authHeader.get());
        String body = requestBody.get();
        assertTrue(body.contains("\"model\":\"test-model\""));
        assertTrue(body.contains("\"max_tokens\":100"));
        assertTrue(body.contains("\"role\":\"system\""));
        assertTrue(body.indexOf("SYSTEM") < body.indexOf("\"hi\"") && body.indexOf("\"hi\"") < body.indexOf("how?"));
    }

    @Test
    void noKeyMeansNoAuthorizationHeader() {
        client("").complete("SYSTEM", List.of(new ChatMessage("user", "hi")));

        assertNull(authHeader.get());
    }

    @Test
    void aRateLimitBecomesAFriendlyServiceUnavailableError() {
        status = 429;
        responseBody = "{\"error\":{\"message\":\"rate limited\"}}";

        AssistantUnavailableException e = assertThrows(AssistantUnavailableException.class,
                () -> client("k").complete("S", List.of(new ChatMessage("user", "hi"))));
        assertEquals(503, e.getStatus().value());
        assertTrue(e.getMessage().contains("usage limit"));
    }

    @Test
    void aRejectedKeyIsReportedAsSuch() {
        status = 401;
        responseBody = "{}";

        AssistantUnavailableException e = assertThrows(AssistantUnavailableException.class,
                () -> client("bad").complete("S", List.of(new ChatMessage("user", "hi"))));
        assertTrue(e.getMessage().contains("API key"));
    }

    @Test
    void aReplyCutOffAtTheTokenLimitSaysSo() {
        responseBody = "{\"choices\":[{\"message\":{\"content\":\"First do this, then\"},\"finish_reason\":\"length\"}]}";

        String reply = client("k").complete("S", List.of(new ChatMessage("user", "hi")));

        assertEquals("First do this, then … (answer cut short; ask for the rest)", reply);
    }

    @Test
    void aProviderThatStopsMidReplyTimesOutWithAClearMessage() {
        server.removeContext("/");
        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 0);                 // headers sent, then the body never finishes
            exchange.getResponseBody().write("{\"choices\":".getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().flush();
            try { Thread.sleep(3000); } catch (InterruptedException ignored) { }
            exchange.close();
        });
        String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        OpenAiCompatibleLlmClient impatient = new OpenAiCompatibleLlmClient(new AssistantProperties(base, "k", "m", 100, 1, 10));

        AssistantUnavailableException e = assertThrows(AssistantUnavailableException.class,
                () -> impatient.complete("S", List.of(new ChatMessage("user", "hi"))));
        assertTrue(e.getMessage().contains("did not answer in time"), e.getMessage());
    }

    @Test
    void anEmptyReplyIsAnError() {
        responseBody = "{\"choices\":[]}";

        assertThrows(AssistantUnavailableException.class,
                () -> client("k").complete("S", List.of(new ChatMessage("user", "hi"))));
    }

    @Test
    void anUnconfiguredClientRefusesWithoutCallingAnything() {
        OpenAiCompatibleLlmClient off = new OpenAiCompatibleLlmClient(new AssistantProperties("", "", "", 100, 5, 10));

        assertThrows(AssistantUnavailableException.class, () -> off.complete("S", List.of(new ChatMessage("user", "hi"))));
        assertNull(path.get());
    }

    @Test
    void propertiesAreOnlyEnabledWithAnAddressAndAModel() {
        assertTrue(new AssistantProperties("http://x", "", "m", 1, 1, 1).enabled());
        assertFalse(new AssistantProperties("http://x", "key", " ", 1, 1, 1).enabled());
        assertFalse(new AssistantProperties(null, "key", "m", 1, 1, 1).enabled());
        assertThrows(IllegalArgumentException.class, () -> new AssistantProperties("", "", "", 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new AssistantProperties("", "", "", 1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new AssistantProperties("", "", "", 1, 1, 0));
    }
}
