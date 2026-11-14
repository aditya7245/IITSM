package com.iitsm.controller;

import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.SlaStatus;
import com.iitsm.repository.IncidentRepository;
import com.iitsm.service.SlaMonitorService;
import com.iitsm.support.AbstractApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The fixes made after the pre-submission audit, each checked through the real API: duplicate privacy, staff view
 * of duplicates, hand-over after acknowledgement, deactivation guard, duplicate chains, text limits, team-lead
 * notes, reporter exclusion, SLA re-evaluation after a priority change, optimistic locking and login throttling.
 */
class AuditFixesApiTest extends AbstractApiTest {

    @Autowired IncidentRepository incidents;
    @Autowired SlaMonitorService slaMonitor;
    @Autowired JdbcTemplate jdbc;

    private long payment(String reporter, String title) throws Exception {
        return submitIncident(reporter, title, title, "Application", "Payment Application", 3, 3, 250);
    }

    private String json(String format, Object... args) {
        return format.formatted(args);
    }

    // ------------------------------------------------------------------ duplicates

    @Test
    void anEmployeeOnlySeesPossibleDuplicatesTheyMayOpen() throws Exception {
        long own = payment("emp1", "Payment application unavailable");

        String others = body(postAs("emp2", "/api/incidents", createJson("Payment transaction failing")).andExpect(status().isCreated()));
        assertEquals(List.of(), list(others, "$.possibleDuplicates"), "emp1's incident must not leak to emp2");

        String mine = body(postAs("emp1", "/api/incidents", createJson("Unable to make payment")).andExpect(status().isCreated()));
        List<Integer> ids = list(mine, "$.possibleDuplicates[*].id");
        assertTrue(ids.contains((int) own), "the reporter's own similar incident is still shown: " + ids);
        assertEquals(1, ids.size(), "but not emp2's: " + ids);
    }

    @Test
    void adminsSeeSimilarIncidentsAndEmployeesMayNot() throws Exception {
        long first = payment("emp1", "Payment application unavailable");
        long second = number(body(postAs("emp2", "/api/incidents", createJson("Payment transaction failing"))
                .andExpect(status().isCreated())), "$.incident.id");

        List<Integer> similar = list(body(getAs("admin", "/api/incidents/" + second + "/similar").andExpect(status().isOk())), "$[*].id");
        assertEquals(List.of((int) first), similar);
        getAs("emp2", "/api/incidents/" + second + "/similar").andExpect(status().isForbidden());
    }

    @Test
    void anIncidentCannotBeLinkedToADuplicateOrToACancelledIncident() throws Exception {
        long a = payment("emp1", "Payment application unavailable");
        long b = payment("emp1", "Payment application down");
        long c = payment("emp2", "Payment portal unavailable");
        postAs("admin", "/api/incidents/" + b + "/duplicate", json("{\"originalId\": %d}", a)).andExpect(status().isOk());

        String chain = body(postAs("admin", "/api/incidents/" + c + "/duplicate", json("{\"originalId\": %d}", b))
                .andExpect(status().isConflict()));
        assertTrue(text(chain, "$.message").contains("is itself a duplicate of"), chain);

        long d = payment("emp2", "Payment terminal unavailable");
        postAs("emp2", "/api/incidents/" + d + "/cancel").andExpect(status().isOk());
        postAs("admin", "/api/incidents/" + c + "/duplicate", json("{\"originalId\": %d}", d)).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ hand-over and deactivation

    @Test
    void anAdminCanHandOverWorkThatHasAlreadyStarted() throws Exception {
        long id = payment("emp1", "Payment application unavailable");
        assertEquals("Kiran Tech", text(body(getAs("admin", "/api/incidents/" + id)), "$.assignee.name"));
        postAs("tech1", "/api/incidents/" + id + "/acknowledge").andExpect(status().isOk());
        postAs("tech1", "/api/incidents/" + id + "/start").andExpect(status().isOk());

        String handed = body(postAs("admin", "/api/incidents/" + id + "/reassign",
                json("{\"technicianId\": %d}", user("tech3").getId())).andExpect(status().isOk()));
        assertEquals("IN_PROGRESS", text(handed, "$.status"));
        assertEquals("Dev Tech", text(handed, "$.assignee.name"));

        postAs("tech1", "/api/incidents/" + id + "/resolve", "{\"resolution\": \"x\"}").andExpect(status().isForbidden());
        postAs("tech3", "/api/incidents/" + id + "/resolve", "{\"resolution\": \"Restarted the gateway\"}").andExpect(status().isOk());
    }

    @Test
    void aTechnicianWithOpenWorkCannotBeDeactivatedUntilItIsReassigned() throws Exception {
        long id = payment("emp1", "Payment application unavailable");
        long tech1 = user("tech1").getId();
        String deactivate = "{\"fullName\": \"Kiran Tech\", \"active\": false, \"department\": null}";

        String refused = body(putAs("admin", "/api/admin/users/" + tech1, deactivate).andExpect(status().isBadRequest()));
        assertTrue(text(refused, "$.message").contains("still has 1 open incident"), refused);

        postAs("admin", "/api/incidents/" + id + "/reassign", json("{\"technicianId\": %d}", user("tech3").getId()))
                .andExpect(status().isOk());
        putAs("admin", "/api/admin/users/" + tech1, deactivate).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ smaller rules

    @Test
    void aTechnicianIsNeverAssignedTheirOwnReport() throws Exception {
        long id = payment("tech1", "Payment application unavailable");   // tech1 has the best skill for this
        String detail = body(getAs("admin", "/api/incidents/" + id));
        assertNotEquals("Kiran Tech", text(detail, "$.assignee.name"));
    }

    @Test
    void theTeamLeadOfTheAssigneeMayAddInternalNotes() throws Exception {
        long id = payment("emp1", "Payment application unavailable");     // assigned to tech1, Meera's team
        String note = "{\"type\": \"NOTE\", \"body\": \"Called the vendor\"}";
        postAs("lead.app", "/api/incidents/" + id + "/comments", note).andExpect(status().isCreated());
        postAs("tech2", "/api/incidents/" + id + "/comments", note).andExpect(status().isForbidden());
    }

    @Test
    void freeTextHasALengthLimit() throws Exception {
        String tooLong = "x".repeat(5001);
        String request = json("{\"title\": \"Long\", \"description\": \"%s\", \"categoryId\": %d, \"serviceId\": %d, "
                + "\"impact\": 1, \"urgency\": 1, \"affectedUsers\": 1}", tooLong, categoryId("Hardware"), serviceId("Corporate Email"));
        postAs("emp1", "/api/incidents", request).andExpect(status().isBadRequest());
    }

    @Test
    void loweringThePriorityAlsoLowersAnSlaStatusThatNoLongerApplies() throws Exception {
        long id = payment("emp1", "Payment application unavailable");     // P1: 5 min response, 30 min resolution
        clock.advance(Duration.ofMinutes(31));
        slaMonitor.runOnce();
        assertEquals("VIOLATED", text(body(getAs("admin", "/api/incidents/" + id)), "$.slaStatus"));

        String lowered = body(postAs("admin", "/api/incidents/" + id + "/priority", "{\"priority\": \"P4\"}")
                .andExpect(status().isOk()));
        assertEquals(SlaStatus.ON_TRACK.name(), text(lowered, "$.slaStatus"));   // 31 of 240 minutes used
    }

    @Test
    void aSaveFromAnOutOfDateCopyIsRefused() throws Exception {
        long id = payment("emp1", "Payment application unavailable");
        Incident copy = incidents.findById(id).orElseThrow();
        jdbc.update("update incidents set version = version + 1 where id = ?", id);   // someone else saved meanwhile

        copy.raiseSlaStatus(SlaStatus.AT_RISK);
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> incidents.saveAndFlush(copy));
    }

    @Test
    void repeatedFailedLoginsAreThrottled() throws Exception {
        String email = "throttle" + System.nanoTime() + "@example.com";
        postAs(null, "/api/auth/register", json("{\"email\": \"%s\", \"password\": \"Password@123\", \"fullName\": \"T\"}", email))
                .andExpect(status().isCreated());
        for (int i = 0; i < 10; i++) {
            loginWith(email, "wrong" + i).andExpect(status().isUnauthorized());
        }
        loginWith(email, "Password@123").andExpect(status().isTooManyRequests());
        clock.advance(Duration.ofMinutes(16));
        loginWith(email, "Password@123").andExpect(status().isOk());
    }

    private String createJson(String title) {
        return json("{\"title\": \"%s\", \"description\": \"%s\", \"categoryId\": %d, \"serviceId\": %d, "
                + "\"impact\": 2, \"urgency\": 2, \"affectedUsers\": 5}", title, title, categoryId("Application"),
                serviceId("Payment Application"));
    }
}
