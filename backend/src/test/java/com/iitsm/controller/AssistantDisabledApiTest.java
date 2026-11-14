package com.iitsm.controller;

import com.iitsm.support.AbstractApiTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** With no AI provider configured (the default), the guide reports itself off and refuses questions with 503. */
class AssistantDisabledApiTest extends AbstractApiTest {

    @Test
    void statusSaysTheGuideIsOff() throws Exception {
        String json = body(getAs("emp1", "/api/assistant/status").andExpect(status().isOk()));
        assertEquals(Boolean.FALSE, JsonPath.read(json, "$.enabled"));
    }

    @Test
    void questionsAreRefusedAsUnavailable() throws Exception {
        String json = body(postAs("emp1", "/api/assistant/chat",
                "{\"messages\": [{\"role\": \"user\", \"content\": \"Hi\"}], \"page\": \"/my-incidents\"}")
                .andExpect(status().isServiceUnavailable()));
        assertEquals("The AI guide is not configured on this server", text(json, "$.message"));
    }
}
