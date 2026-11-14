package com.iitsm.workflow;

import com.iitsm.service.EscalationService;
import com.iitsm.service.SlaMonitorService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Demo workflow 3: a similar report is flagged as a possible duplicate and an administrator marks it as one, and a P1
 * incident that nobody acknowledges becomes at risk, violated and then escalates through the three levels.
 */
class DuplicatesAndEscalationWorkflowTest extends WorkflowTestBase {

    @Autowired SlaMonitorService monitor;
    @Autowired EscalationService escalation;

    private String reportPaymentOutage(String reporter, String title) throws Exception {
        return report(reporter, title, "Users cannot complete payment transactions",
                "Application", "Payment Application", 3, 3, 250);
    }

    private long level(long id) throws Exception {
        return number(incident(id), "$.escalationLevel");
    }

    @Test
    void aSimilarReportIsFlaggedAndTheAdministratorMarksItAsADuplicate() throws Exception {
        String first = reportPaymentOutage("emp1", "Payment application outage");
        String second = reportPaymentOutage("emp2", "Payment application down");

        // emp2 may not open emp1's incident, so it is not shown to them; the administrator sees it instead
        assertEquals(List.of(), list(second, "$.possibleDuplicates"));
        assertEquals("ASSIGNED", text(second, "$.incident.status"));          // only a warning: the incident exists

        long copy = number(second, "$.incident.id");
        String similar = body(getAs("admin", url(copy, "similar")).andExpect(status().isOk()));
        assertEquals(List.of(text(first, "$.incident.incidentNumber")), list(similar, "$[*].number"));
        double score = ((Number) JsonPath.read(similar, "$[0].score")).doubleValue();
        assertEquals(1.0, score, 1e-9, "\"outage\" and \"down\" are synonyms, so the reports match fully");

        String marked = body(postAs("admin", url(copy, "duplicate"),
                "{\"originalId\": " + number(first, "$.incident.id") + "}").andExpect(status().isOk()));

        assertEquals("DUPLICATE", text(marked, "$.status"));
        assertEquals(text(first, "$.incident.incidentNumber"), text(marked, "$.duplicateOfNumber"));
    }

    @Test
    void anUnacknowledgedP1BecomesAtRiskThenViolatedThenEscalatesThroughAllThreeLevels() throws Exception {
        String created = reportPaymentOutage("emp1", "Payment gateway timeout");
        long id = number(created, "$.incident.id");
        assertEquals("P1", text(created, "$.incident.priority"));
        assertEquals(0, level(id));

        clock.advance(Duration.ofMinutes(4));                                  // 80% of the 5 minute response window
        monitor.runOnce();
        escalation.runOnce();
        assertEquals("AT_RISK", text(incident(id), "$.slaStatus"));
        assertEquals(0, level(id));

        clock.advance(Duration.ofMinutes(1));                                  // exactly the deadline: not violated yet
        monitor.runOnce();
        escalation.runOnce();
        assertEquals("AT_RISK", text(incident(id), "$.slaStatus"));
        assertEquals(1, level(id));                                            // level 1: the team lead

        clock.advance(Duration.ofSeconds(1));                                  // one second late
        monitor.runOnce();
        assertEquals("VIOLATED", text(incident(id), "$.slaStatus"));

        clock.advance(Duration.ofMinutes(5));                                  // one level timeout later
        escalation.runOnce();
        assertEquals(2, level(id));                                            // level 2: the administrators

        clock.advance(Duration.ofMinutes(5));
        escalation.runOnce();
        assertEquals(3, level(id));                                            // level 3: the IT manager

        clock.advance(Duration.ofDays(1));
        escalation.runOnce();
        assertEquals(3, level(id));                                            // the chain ends there

        String after = incident(id);
        assertEquals("ASSIGNED", text(after, "$.status"));                     // escalating only informs, nobody is reassigned
        assertEquals("Kiran Tech", text(after, "$.assignee.name"));

        List<String> actions = actions(id);
        assertEquals(3, actions.stream().filter("ESCALATED"::equals).count());
        assertTrue(actions.contains("SLA_WARNING"), actions.toString());
        assertTrue(actions.contains("SLA_VIOLATED"), actions.toString());
    }

    @Test
    void aDuplicateIsNeverEscalated() throws Exception {
        long original = number(reportPaymentOutage("emp1", "Payment application outage"), "$.incident.id");
        long copy = number(reportPaymentOutage("emp2", "Payment application down"), "$.incident.id");
        postAs("admin", url(copy, "duplicate"), "{\"originalId\": " + original + "}").andExpect(status().isOk());

        clock.advance(Duration.ofMinutes(5));
        escalation.runOnce();

        assertEquals(1, level(original));
        assertEquals(0, level(copy));
    }
}
