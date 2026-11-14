package com.iitsm.workflow;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Demo workflow 1: an employee reports a payment outage, which is prioritised, assigned and recorded. */
class ReportingAnOutageWorkflowTest extends WorkflowTestBase {

    private long activeOf(String technician) throws Exception {
        return number(body(getAs(technician, "/api/technicians/me/workload").andExpect(status().isOk())), "$.active");
    }

    private String reportPaymentOutage() throws Exception {
        return report("emp1", "Payment application outage", "Users cannot complete payment transactions",
                "Application", "Payment Application", 3, 3, 250);
    }

    @Test
    void thePaymentOutageIsAP1WithItsDeadlinesAndGoesToTheSkilledTechnician() throws Exception {
        String created = reportPaymentOutage();

        assertTrue(text(created, "$.incident.incidentNumber").matches("INC\\d+"));
        assertEquals(45, number(created, "$.incident.priorityScore"));                  // 3 x 3 x criticality 5
        assertEquals("P1", text(created, "$.incident.priority"));
        assertEquals("ASSIGNED", text(created, "$.incident.status"));
        assertEquals("Kiran Tech", text(created, "$.incident.assignee.name"));
        assertEquals("Asha Employee", text(created, "$.incident.reporter.name"));
        assertTrue(list(created, "$.possibleDuplicates").isEmpty());

        assertEquals(T0, time(created, "$.incident.createdAt"));
        assertEquals(T0.plus(Duration.ofMinutes(5)), time(created, "$.incident.responseDeadline"));
        assertEquals(T0.plus(Duration.ofMinutes(30)), time(created, "$.incident.resolutionDeadline"));
        assertEquals("ON_TRACK", text(created, "$.incident.slaStatus"));
    }

    @Test
    void theTechnicianSeesItInTheirWorkloadAndTheHistoryShowsWhatTheSystemDid() throws Exception {
        long before = activeOf("tech1");

        long id = number(reportPaymentOutage(), "$.incident.id");

        assertEquals(before + 1, activeOf("tech1"));
        assertEquals(List.of("CREATED", "CLASSIFIED", "ASSIGNED"), actions(id));

        String history = body(getAs("admin", url(id, "history")).andExpect(status().isOk()));
        assertNotNull(JsonPath.read(history, "$[0].user"));    // the employee reported it
        assertNull(JsonPath.read(history, "$[1].user"));       // classified by the system
        assertNull(JsonPath.read(history, "$[2].user"));       // assigned by the system
    }
}
