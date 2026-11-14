package com.iitsm.workflow;

import com.iitsm.service.SlaMonitorService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Demo workflow 2: a technician works a VPN incident (P2, assigned to tech2), asks the reporter for information while
 * the SLA clock pauses, resolves it, and the reporter verifies and rates it so that it closes.
 */
class WorkingAnIncidentWorkflowTest extends WorkflowTestBase {

    @Autowired SlaMonitorService monitor;

    private String statusOf(long id) throws Exception {
        return text(incident(id), "$.status");
    }

    @Test
    void fromReportToClosedWithAPauseInTheMiddle() throws Exception {
        // 1. Report: a VPN problem is P2 (2 x 2 x criticality 4 = 16) and goes to the only Networking technician.
        String created = report("emp2", "VPN keeps disconnecting", "Remote staff lose the VPN connection every few minutes",
                "Network", "VPN Gateway", 2, 2, 5);
        long id = number(created, "$.incident.id");
        assertEquals(16, number(created, "$.incident.priorityScore"));
        assertEquals("P2", text(created, "$.incident.priority"));
        assertEquals("Sana Tech", text(created, "$.incident.assignee.name"));
        assertEquals(T0.plus(Duration.ofMinutes(15)), time(created, "$.incident.responseDeadline"));
        assertEquals(T0.plus(Duration.ofMinutes(120)), time(created, "$.incident.resolutionDeadline"));

        // 2. The technician picks it up in time.
        clock.advance(Duration.ofMinutes(2));
        postAs("tech2", url(id, "acknowledge")).andExpect(status().isOk());
        assertEquals("ACKNOWLEDGED", statusOf(id));
        postAs("tech2", url(id, "start")).andExpect(status().isOk());
        assertEquals("IN_PROGRESS", statusOf(id));

        // 3. The technician asks the reporter a question: the SLA clock pauses.
        clock.advance(Duration.ofMinutes(3));
        Instant pausedAt = clock.instant();
        postAs("tech2", url(id, "comments"), """
                {"type": "INFO_REQUEST", "body": "Which VPN client version are you using?"}""")
                .andExpect(status().isCreated());
        String waiting = incident(id);
        assertEquals("PENDING_INFO", text(waiting, "$.status"));
        assertEquals(pausedAt, time(waiting, "$.slaPausedAt"));
        Instant responseDeadline = time(waiting, "$.responseDeadline");
        Instant resolutionDeadline = time(waiting, "$.resolutionDeadline");

        // 4. The reporter takes 3 hours, longer than the whole 2 hour resolution window. Waiting does not use up time.
        clock.advance(Duration.ofHours(3));
        monitor.runOnce();
        assertEquals("ON_TRACK", text(incident(id), "$.slaStatus"));

        // 5. The reporter answers: work continues and both deadlines move later by exactly the 3 hours of waiting.
        postAs("emp2", url(id, "comments"), """
                {"type": "INFO_RESPONSE", "body": "Version 5.2 on Windows 11"}""")
                .andExpect(status().isCreated());
        String resumed = incident(id);
        assertEquals("IN_PROGRESS", text(resumed, "$.status"));
        assertNull(JsonPath.read(resumed, "$.slaPausedAt"));
        assertEquals(responseDeadline.plus(Duration.ofHours(3)), time(resumed, "$.responseDeadline"));
        assertEquals(resolutionDeadline.plus(Duration.ofHours(3)), time(resumed, "$.resolutionDeadline"));
        monitor.runOnce();
        assertEquals("ON_TRACK", text(incident(id), "$.slaStatus"));

        // 6. The technician resolves it, the reporter verifies and rates it, and the rating closes it.
        postAs("tech2", url(id, "resolve"), """
                {"resolution": "Updated the VPN client and reset the gateway session"}""").andExpect(status().isOk());
        assertEquals("RESOLVED", statusOf(id));
        postAs("emp2", url(id, "verify")).andExpect(status().isOk());
        assertEquals("VERIFIED", statusOf(id));
        postAs("emp2", url(id, "feedback"), """
                {"rating": 5, "comments": "Fixed quickly"}""").andExpect(status().isCreated());

        String closed = incident(id);
        assertEquals("CLOSED", text(closed, "$.status"));
        assertEquals("ON_TRACK", text(closed, "$.slaStatus"));
        assertNotNull(text(closed, "$.closedAt"));
        assertEquals("Updated the VPN client and reset the gateway session", text(closed, "$.resolution"));

        assertEquals(List.of("CREATED", "CLASSIFIED", "ASSIGNED", "ACKNOWLEDGED", "INVESTIGATION_STARTED",
                "INFO_REQUESTED", "INFO_RECEIVED", "RESOLVED", "VERIFIED", "CLOSED"), actions(id));
    }
}
