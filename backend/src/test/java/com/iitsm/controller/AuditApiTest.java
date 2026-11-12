package com.iitsm.controller;

import com.iitsm.support.AbstractApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The audit endpoint through the real security chain. emp1 reports incident A (history: CREATED by emp1, then
 * CLASSIFIED and ASSIGNED by the system). emp2 reports incident B and an administrator cancels it (CREATED by emp2,
 * CLASSIFIED, ASSIGNED, CANCELLED by the admin). All of it happens on 10 November. Other tests may have left
 * history lines in the database, so every check narrows the search to these two incidents.
 */
class AuditApiTest extends AbstractApiTest {

    long a;
    long b;

    @BeforeEach
    void createHistory() throws Exception {
        a = submitIncident("emp1", "Payment application outage", "Users cannot pay",
                "Application", "Payment Application", 3, 3, 250);
        b = submitIncident("emp2", "Another payment problem", "Cards are declined",
                "Application", "Payment Application", 3, 3, 250);
        postAs("admin", "/api/incidents/" + b + "/cancel").andExpect(status().isOk());
    }

    private String audit(String user, String query) throws Exception {
        return body(getAs(user, "/api/audit" + query).andExpect(status().isOk()));
    }

    private List<String> actions(String user, String query) throws Exception {
        return list(audit(user, query), "$.content[*].action");
    }

    // ---------- the filters ----------

    @Test
    void anAdministratorBrowsesOneIncidentsHistoryNewestFirst() throws Exception {
        assertEquals(List.of("ASSIGNED", "CLASSIFIED", "CREATED"), actions("admin", "?incidentId=" + a));
        assertEquals(List.of("CANCELLED", "ASSIGNED", "CLASSIFIED", "CREATED"), actions("admin", "?incidentId=" + b));
    }

    @Test
    void eachLineCarriesItsDetails() throws Exception {
        String body = audit("admin", "?incidentId=" + b + "&size=1");        // the newest line: the cancellation

        assertEquals("CANCELLED", text(body, "$.content[0].action"));
        assertEquals(b, number(body, "$.content[0].incidentId"));
        assertTrue(text(body, "$.content[0].incidentNumber").matches("INC\\d+"));
        assertEquals("ASSIGNED", text(body, "$.content[0].previousState"));
        assertEquals("CANCELLED", text(body, "$.content[0].newState"));
        assertEquals("System Admin", text(body, "$.content[0].user.name"));
        assertNotNull(text(body, "$.content[0].description"));
        assertNotNull(text(body, "$.content[0].createdAt"));
    }

    @Test
    void linesDoneByTheSystemHaveNoUser() throws Exception {
        String body = audit("admin", "?incidentId=" + a);

        assertNull(com.jayway.jsonpath.JsonPath.read(body, "$.content[0].user"));      // ASSIGNED, by the system
        assertNull(com.jayway.jsonpath.JsonPath.read(body, "$.content[1].user"));      // CLASSIFIED, by the system
        assertEquals("Asha Employee", text(body, "$.content[2].user.name"));           // CREATED, by the reporter
        assertNull(com.jayway.jsonpath.JsonPath.read(body, "$.content[2].previousState"));
    }

    @Test
    void byUser() throws Exception {
        assertEquals(List.of("CREATED"), actions("admin", "?incidentId=" + a + "&userId=" + user("emp1").getId()));
        assertEquals(List.of("CANCELLED"), actions("admin", "?incidentId=" + b + "&userId=" + user("admin").getId()));
        assertEquals(List.of("CREATED"), actions("admin", "?incidentId=" + b + "&userId=" + user("emp2").getId()));
        assertEquals(List.of(), actions("admin", "?incidentId=" + a + "&userId=" + user("emp2").getId()));
    }

    @Test
    void byDateWithBothEndsIncluded() throws Exception {                      // everything happened on 10 November
        assertEquals(3, actions("admin", "?incidentId=" + a + "&from=2026-11-10&to=2026-11-10").size());
        assertEquals(3, actions("admin", "?incidentId=" + a + "&from=2026-11-10").size());
        assertEquals(3, actions("admin", "?incidentId=" + a + "&to=2026-11-10").size());
        assertEquals(List.of(), actions("admin", "?incidentId=" + a + "&from=2026-11-11"));
        assertEquals(List.of(), actions("admin", "?incidentId=" + a + "&to=2026-11-09"));
    }

    @Test
    void thePagesHaveTheRightContentAndTotals() throws Exception {
        String page0 = audit("admin", "?incidentId=" + b + "&size=3&page=0");
        assertEquals(List.of("CANCELLED", "ASSIGNED", "CLASSIFIED"), list(page0, "$.content[*].action"));
        assertEquals(4, number(page0, "$.totalElements"));
        assertEquals(2, number(page0, "$.totalPages"));

        assertEquals(List.of("CREATED"), actions("admin", "?incidentId=" + b + "&size=3&page=1"));
        assertEquals(List.of(), actions("admin", "?incidentId=" + b + "&size=3&page=2"));
    }

    @Test
    void aSortInTheRequestDoesNotChangeTheOrderOrFail() throws Exception {
        assertEquals(List.of("ASSIGNED", "CLASSIFIED", "CREATED"), actions("admin", "?incidentId=" + a + "&sort=action,asc"));
        assertEquals(List.of("ASSIGNED", "CLASSIFIED", "CREATED"), actions("admin", "?incidentId=" + a + "&sort=nonsense"));
    }

    @Test
    void withoutFiltersItStillWorksAndIsPaged() throws Exception {
        String body = audit("admin", "");

        assertEquals(20, number(body, "$.size"));
        assertFalse(list(body, "$.content[*].action").isEmpty());
        assertEquals(100, number(audit("admin", "?size=500"), "$.size"));
    }

    @Test
    void badFilterValuesAreA400() throws Exception {
        getAs("admin", "/api/audit?incidentId=abc").andExpect(status().isBadRequest());
        getAs("admin", "/api/audit?userId=abc").andExpect(status().isBadRequest());
        getAs("admin", "/api/audit?from=garbage").andExpect(status().isBadRequest());
        getAs("admin", "/api/audit?to=2026-13-45").andExpect(status().isBadRequest());
    }

    // ---------- administrators only ----------

    @Test
    void anITManagerIsAnAdministratorToo() throws Exception {
        assertEquals(List.of("ASSIGNED", "CLASSIFIED", "CREATED"), actions("manager", "?incidentId=" + a));
    }

    @Test
    void everyoneElseIsForbiddenAndNoLoginIs401() throws Exception {
        for (String name : List.of("emp1", "emp2", "tech1", "tech2", "lead.app")) {
            getAs(name, "/api/audit").andExpect(status().isForbidden());
            getAs(name, "/api/audit?incidentId=" + a).andExpect(status().isForbidden());
        }
        getAs(null, "/api/audit").andExpect(status().isUnauthorized());
    }
}
