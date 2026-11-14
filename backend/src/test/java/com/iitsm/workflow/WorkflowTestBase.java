package com.iitsm.workflow;

import com.iitsm.support.AbstractApiTest;

import java.time.Instant;
import java.util.List;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared helpers of the workflow tests, which replay the three workflows of the demo script (docs/demo-script.md)
 * through the REST API. Instead of waiting, the tests move the fake clock and call the SLA monitor and the escalation
 * service by hand. The clock starts at {@link #T0} and the time scale is 1, so a P1 must be answered within 5 minutes.
 */
abstract class WorkflowTestBase extends AbstractApiTest {

    static final Instant T0 = Instant.parse("2026-11-10T09:00:00Z");

    /** The user reports an incident through the API; returns the whole response body (it must be a 201). */
    String report(String reporter, String title, String description, String category, String service,
                  int impact, int urgency, int affectedUsers) throws Exception {
        String json = """
                {"title": "%s", "description": "%s", "categoryId": %d, "serviceId": %d,
                 "impact": %d, "urgency": %d, "affectedUsers": %d}
                """.formatted(title, description, categoryId(category), serviceId(service), impact, urgency, affectedUsers);
        return body(postAs(reporter, "/api/incidents", json).andExpect(status().isCreated()));
    }

    String url(long id, String action) {
        return "/api/incidents/" + id + (action.isEmpty() ? "" : "/" + action);
    }

    /** The incident as an administrator sees it. */
    String incident(long id) throws Exception {
        return body(getAs("admin", url(id, "")).andExpect(status().isOk()));
    }

    /** The actions of the incident's history, oldest first. */
    List<String> actions(long id) throws Exception {
        return list(body(getAs("admin", url(id, "history")).andExpect(status().isOk())), "$[*].action");
    }

    /** A time value of a JSON response. */
    Instant time(String json, String path) {
        return Instant.parse(text(json, path));
    }
}
