package com.iitsm.controller;

import com.iitsm.service.SlaMonitorService;
import com.iitsm.support.AbstractApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Searching the incident list through the real security chain. Three incidents are created on three different days:
 * <pre>
 *   A  10 Nov  emp1  Payment Application / Application     P1  tech1  ACKNOWLEDGED
 *   B  12 Nov  emp1  VPN Gateway         / Authentication  P2  tech2  ASSIGNED
 *   C  14 Nov  emp2  Internal Wiki       / Application     P4  tech2  ASSIGNED
 * </pre>
 * tech1 and the team lead lead.app are in Application Support; tech2 and tech3 are in Infrastructure.
 */
class IncidentSearchApiTest extends AbstractApiTest {

    @Autowired SlaMonitorService monitor;

    String numberA;
    String numberB;
    String numberC;

    // ---------- the data ----------

    private void seed() throws Exception {
        long a = submitIncident("emp1", "Payment application outage", "Users cannot complete payment transactions",
                "Application", "Payment Application", 3, 3, 250);
        postAs("tech1", "/api/incidents/" + a + "/acknowledge").andExpect(status().isOk());

        clock.advance(Duration.ofDays(2));
        long b = submitIncident("emp1", "VPN login failing", "Staff cannot sign in to the VPN",
                "Authentication", "VPN Gateway", 2, 2, 40);
        assign(b, "tech2", "P2");

        clock.advance(Duration.ofDays(2));
        long c = submitIncident("emp2", "Wiki page not loading", "The documentation wiki shows an empty page",
                "Application", "Internal Wiki", 1, 1, 5);
        assign(c, "tech2", "P4");

        numberA = numberOf(a);
        numberB = numberOf(b);
        numberC = numberOf(c);
    }

    /** An administrator hands the incident to a technician and sets its priority, so the data does not depend on automatic choices. */
    private void assign(long id, String technician, String priority) throws Exception {
        String current = body(getAs("admin", "/api/incidents/" + id).andExpect(status().isOk()));
        boolean alreadyHeld = current.contains("\"assignee\":{\"id\":" + user(technician).getId() + ",");
        if (!alreadyHeld) {
            postAs("admin", "/api/incidents/" + id + "/reassign", "{\"technicianId\": " + user(technician).getId() + "}")
                    .andExpect(status().isOk());
        }
        postAs("admin", "/api/incidents/" + id + "/priority", "{\"priority\": \"" + priority + "\"}").andExpect(status().isOk());
    }

    private String numberOf(long id) throws Exception {
        return text(body(getAs("admin", "/api/incidents/" + id).andExpect(status().isOk())), "$.incidentNumber");
    }

    /** The incident numbers a user gets back for a query, in the order returned. */
    private List<String> found(String user, String query) throws Exception {
        return list(body(getAs(user, "/api/incidents" + query).andExpect(status().isOk())), "$.content[*].incidentNumber");
    }

    private String page(String user, String query) throws Exception {
        return body(getAs(user, "/api/incidents" + query).andExpect(status().isOk()));
    }

    // ---------- each filter alone (as an administrator, who sees everything) ----------

    @Test
    void withoutFiltersAnAdministratorGetsAllThreeNewestFirst() throws Exception {
        seed();

        assertEquals(List.of(numberC, numberB, numberA), found("admin", ""));
    }

    @Test
    void eachRowCarriesTheListFields() throws Exception {
        seed();

        String body = page("admin", "?incidentNumber=" + numberB);

        assertEquals(numberB, text(body, "$.content[0].incidentNumber"));
        assertEquals("VPN login failing", text(body, "$.content[0].title"));
        assertEquals("P2", text(body, "$.content[0].priority"));
        assertEquals("ASSIGNED", text(body, "$.content[0].status"));
        assertEquals("ON_TRACK", text(body, "$.content[0].slaStatus"));
        assertEquals("Asha Employee", text(body, "$.content[0].reporter.name"));
        assertEquals("Sana Tech", text(body, "$.content[0].assignee.name"));
        assertEquals("VPN Gateway", text(body, "$.content[0].service.name"));
        assertEquals("Authentication", text(body, "$.content[0].category.name"));
    }

    @Test
    void byIncidentNumberIgnoringCaseAndMatchingPartOfTheNumber() throws Exception {
        seed();

        assertEquals(List.of(numberB), found("admin", "?incidentNumber=" + numberB.toLowerCase()));
        assertEquals(List.of(numberB), found("admin", "?incidentNumber=" + numberB.toUpperCase()));
        assertEquals(List.of(numberC, numberB, numberA), found("admin", "?incidentNumber=inc"));
        assertEquals(List.of(), found("admin", "?incidentNumber=ZZZ"));
        assertEquals(List.of(numberC, numberB, numberA), found("admin", "?incidentNumber="));      // blank is not a filter
    }

    @Test
    void thePercentSignInANumberSearchMeansItselfNotEverything() throws Exception {
        seed();

        for (String wildcard : List.of("%", "_", "%%", "I_C")) {
            String body = body(send("admin", get("/api/incidents").param("incidentNumber", wildcard)).andExpect(status().isOk()));
            assertEquals(List.of(), list(body, "$.content[*].incidentNumber"), "searching for " + wildcard);
        }
    }

    @Test
    void byStatus() throws Exception {
        seed();

        assertEquals(List.of(numberA), found("admin", "?status=ACKNOWLEDGED"));
        assertEquals(List.of(numberC, numberB), found("admin", "?status=ASSIGNED"));
        assertEquals(List.of(), found("admin", "?status=CLOSED"));
    }

    @Test
    void byPriority() throws Exception {
        seed();

        assertEquals(List.of(numberA), found("admin", "?priority=P1"));
        assertEquals(List.of(numberB), found("admin", "?priority=P2"));
        assertEquals(List.of(), found("admin", "?priority=P3"));
        assertEquals(List.of(numberC), found("admin", "?priority=P4"));
    }

    @Test
    void byCategory() throws Exception {
        seed();

        assertEquals(List.of(numberB), found("admin", "?categoryId=" + categoryId("Authentication")));
        assertEquals(List.of(numberC, numberA), found("admin", "?categoryId=" + categoryId("Application")));
    }

    @Test
    void byService() throws Exception {
        seed();

        assertEquals(List.of(numberA), found("admin", "?serviceId=" + serviceId("Payment Application")));
        assertEquals(List.of(numberB), found("admin", "?serviceId=" + serviceId("VPN Gateway")));
        assertEquals(List.of(numberC), found("admin", "?serviceId=" + serviceId("Internal Wiki")));
        assertEquals(List.of(), found("admin", "?serviceId=" + serviceId("Corporate Email")));
    }

    @Test
    void byTechnician() throws Exception {
        seed();

        assertEquals(List.of(numberA), found("admin", "?technicianId=" + user("tech1").getId()));
        assertEquals(List.of(numberC, numberB), found("admin", "?technicianId=" + user("tech2").getId()));
        assertEquals(List.of(), found("admin", "?technicianId=" + user("tech3").getId()));
    }

    @Test
    void bySlaStatus() throws Exception {
        long late = submitIncident("emp1", "Payment application outage", "Users cannot pay",
                "Application", "Payment Application", 3, 3, 250);          // P1: respond within 5 minutes
        clock.advance(Duration.ofMinutes(10));
        monitor.runOnce();                                                  // the response deadline has passed
        long fresh = submitIncident("emp2", "Another payment problem", "Cards are declined",
                "Application", "Payment Application", 3, 3, 250);

        assertEquals(1, found("admin", "?slaStatus=VIOLATED").size());
        assertEquals(1, found("admin", "?slaStatus=ON_TRACK").size());
        assertEquals(List.of(), found("admin", "?slaStatus=AT_RISK"));
        String violated = page("admin", "?slaStatus=VIOLATED");
        assertEquals(late, number(violated, "$.content[0].id"));
        assertEquals(fresh, number(page("admin", "?slaStatus=ON_TRACK"), "$.content[0].id"));
    }

    @Test
    void byCreationDateWithBothEndsIncluded() throws Exception {
        seed();                                                             // A on 10 Nov, B on 12 Nov, C on 14 Nov

        assertEquals(List.of(numberC, numberB), found("admin", "?createdFrom=2026-11-12"));
        assertEquals(List.of(numberB, numberA), found("admin", "?createdTo=2026-11-12"));
        assertEquals(List.of(numberB), found("admin", "?createdFrom=2026-11-12&createdTo=2026-11-12"));
        assertEquals(List.of(numberC, numberB, numberA), found("admin", "?createdFrom=2026-11-10&createdTo=2026-11-14"));
        assertEquals(List.of(), found("admin", "?createdTo=2026-11-09"));
        assertEquals(List.of(), found("admin", "?createdFrom=2026-11-15"));
        assertEquals(List.of(), found("admin", "?createdFrom=2026-11-14&createdTo=2026-11-10"));   // an empty range
    }

    // ---------- several filters together ----------

    @Test
    void severalFiltersNarrowTheResultTogether() throws Exception {
        seed();
        long tech1 = user("tech1").getId();
        long tech2 = user("tech2").getId();

        assertEquals(List.of(numberC), found("admin", "?status=ASSIGNED&categoryId=" + categoryId("Application")));
        assertEquals(List.of(numberA), found("admin", "?priority=P1&technicianId=" + tech1));
        assertEquals(List.of(numberB), found("admin", "?technicianId=" + tech2 + "&priority=P2"));
        assertEquals(List.of(numberC), found("admin",
                "?createdFrom=2026-11-12&serviceId=" + serviceId("Internal Wiki") + "&status=ASSIGNED"));
        assertEquals(List.of(numberC), found("admin", "?incidentNumber=" + numberC.toLowerCase() + "&priority=P4&technicianId=" + tech2));
        assertEquals(List.of(), found("admin", "?status=ACKNOWLEDGED&technicianId=" + tech2));
    }

    // ---------- pages and sorting ----------

    @Test
    void pagesHaveTheRightContentAndTotals() throws Exception {
        seed();

        String first = page("admin", "?size=2&page=0");
        assertEquals(List.of(numberC, numberB), list(first, "$.content[*].incidentNumber"));
        assertEquals(0, number(first, "$.page"));
        assertEquals(2, number(first, "$.size"));
        assertEquals(3, number(first, "$.totalElements"));
        assertEquals(2, number(first, "$.totalPages"));

        String second = page("admin", "?size=2&page=1");
        assertEquals(List.of(numberA), list(second, "$.content[*].incidentNumber"));
        assertEquals(1, number(second, "$.page"));

        String beyond = page("admin", "?size=2&page=5");
        assertEquals(List.of(), list(beyond, "$.content[*].incidentNumber"));
        assertEquals(3, number(beyond, "$.totalElements"));
    }

    @Test
    void theDefaultPageSizeIsTwentyAndTheLargestIsOneHundred() throws Exception {
        seed();

        assertEquals(20, number(page("admin", ""), "$.size"));
        assertEquals(100, number(page("admin", "?size=500"), "$.size"));
        assertEquals(100, number(page("admin", "?size=100"), "$.size"));
        assertEquals(1, number(page("admin", "?size=1"), "$.size"));
    }

    @Test
    void theSortCanBeChanged() throws Exception {
        seed();

        assertEquals(List.of(numberA, numberB, numberC), found("admin", "?sort=createdAt,asc"));
        assertEquals(List.of(numberC, numberB, numberA), found("admin", "?sort=createdAt,desc"));
        assertEquals(List.of(numberA, numberB, numberC), found("admin", "?sort=incidentNumber,asc"));
        assertEquals(List.of(numberC, numberB, numberA), found("admin", "?sort=incidentNumber,desc"));
    }

    @Test
    void incidentsCreatedAtTheSameMomentStillPageWithoutRepeatsOrGaps() throws Exception {
        long first = submitIncident("emp1", "First", "One", "Application", "Payment Application", 3, 3, 10);
        long second = submitIncident("emp1", "Second", "Two", "Application", "Payment Application", 3, 3, 10);
        long third = submitIncident("emp1", "Third", "Three", "Application", "Payment Application", 3, 3, 10);

        List<Number> ids = new java.util.ArrayList<>();
        for (int page = 0; page < 3; page++) {
            ids.addAll(list(page("admin", "?size=1&page=" + page), "$.content[*].id"));
        }

        assertEquals(List.of(third, second, first), ids.stream().map(Number::longValue).toList());   // same time: newest id first
    }

    @Test
    void anUnknownSortColumnOrABadValueIsA400() throws Exception {
        getAs("admin", "/api/incidents?sort=nonsense,asc").andExpect(status().isBadRequest());
        getAs("admin", "/api/incidents?sort=reporter,asc").andExpect(status().isBadRequest());
        getAs("admin", "/api/incidents?status=NOPE").andExpect(status().isBadRequest());
        getAs("admin", "/api/incidents?priority=P9").andExpect(status().isBadRequest());
        getAs("admin", "/api/incidents?slaStatus=LATE").andExpect(status().isBadRequest());
        getAs("admin", "/api/incidents?categoryId=abc").andExpect(status().isBadRequest());
        getAs("admin", "/api/incidents?createdFrom=garbage").andExpect(status().isBadRequest());
        getAs("admin", "/api/incidents?createdTo=2026-13-45").andExpect(status().isBadRequest());
    }

    @Test
    void withoutATokenTheListIs401() throws Exception {
        getAs(null, "/api/incidents").andExpect(status().isUnauthorized());
    }

    // ---------- each person only sees their own ----------

    @Test
    void anEmployeeSeesOnlyTheIncidentsTheyReported() throws Exception {
        seed();

        assertEquals(List.of(numberB, numberA), found("emp1", ""));
        assertEquals(List.of(numberC), found("emp2", ""));
    }

    @Test
    void aTechnicianSeesOnlyTheIncidentsAssignedToThem() throws Exception {
        seed();

        assertEquals(List.of(numberA), found("tech1", ""));
        assertEquals(List.of(numberC, numberB), found("tech2", ""));
        assertEquals(List.of(), found("tech3", ""));
    }

    @Test
    void aTeamLeadSeesTheirTeamsIncidentsButNotAnotherTeams() throws Exception {
        seed();                                                             // tech1 is in the team lead.app leads; tech2 is not

        assertEquals(List.of(numberA), found("lead.app", ""));
        assertEquals(List.of(), found("lead.app", "?technicianId=" + user("tech2").getId()));
    }

    @Test
    void aTeamLeadAlsoSeesWhatIsAssignedToThemselves() throws Exception {
        seed();
        postAs("admin", "/api/incidents/" + idOf(numberC) + "/reassign", "{\"technicianId\": " + user("lead.app").getId() + "}")
                .andExpect(status().isOk());

        assertEquals(List.of(numberC, numberA), found("lead.app", ""));
    }

    @Test
    void administratorsAndITManagersSeeEverything() throws Exception {
        seed();

        assertEquals(List.of(numberC, numberB, numberA), found("admin", ""));
        assertEquals(List.of(numberC, numberB, numberA), found("manager", ""));
    }

    @Test
    void filtersCanOnlyNarrowWhatAPersonAlreadySees() throws Exception {
        seed();

        assertEquals(List.of(), found("emp1", "?serviceId=" + serviceId("Internal Wiki")));          // C belongs to emp2
        assertEquals(List.of(numberA), found("emp1", "?serviceId=" + serviceId("Payment Application")));
        assertEquals(List.of(), found("tech1", "?technicianId=" + user("tech2").getId()));
        assertEquals(List.of(), found("tech2", "?incidentNumber=" + numberA.toLowerCase()));
        assertEquals(List.of(numberB), found("tech2", "?priority=P2"));
        assertEquals(3, number(page("admin", ""), "$.totalElements"));
        assertEquals(2, number(page("emp1", ""), "$.totalElements"));        // the totals are scoped too
    }

    private long idOf(String number) throws Exception {
        return number(page("admin", "?incidentNumber=" + number.toLowerCase()), "$.content[0].id");
    }

    // ---------- the technician's workload ----------

    @Test
    void aTechnicianSeesTheirActiveIncidentsAndLimit() throws Exception {
        seed();                                                             // tech1: A (acknowledged); tech2: B and C

        String tech1 = body(getAs("tech1", "/api/technicians/me/workload").andExpect(status().isOk()));
        assertEquals(1, number(tech1, "$.active"));
        assertEquals(5, number(tech1, "$.max"));

        String tech2 = body(getAs("tech2", "/api/technicians/me/workload").andExpect(status().isOk()));
        assertEquals(2, number(tech2, "$.active"));
        assertEquals(5, number(tech2, "$.max"));

        String tech3 = body(getAs("tech3", "/api/technicians/me/workload").andExpect(status().isOk()));
        assertEquals(0, number(tech3, "$.active"));
        assertEquals(3, number(tech3, "$.max"));
    }

    @Test
    void aTeamLeadHasAWorkloadToo() throws Exception {
        String lead = body(getAs("lead.app", "/api/technicians/me/workload").andExpect(status().isOk()));

        assertEquals(0, number(lead, "$.active"));
        assertEquals(5, number(lead, "$.max"));
    }

    @Test
    void aResolvedIncidentNoLongerCountsTowardTheWorkload() throws Exception {
        long id = submitIncident("emp1", "Payment application outage", "Users cannot pay",
                "Application", "Payment Application", 3, 3, 250);          // goes to tech1
        assertEquals(1, number(body(getAs("tech1", "/api/technicians/me/workload").andExpect(status().isOk())), "$.active"));

        postAs("tech1", "/api/incidents/" + id + "/acknowledge").andExpect(status().isOk());
        postAs("tech1", "/api/incidents/" + id + "/start").andExpect(status().isOk());
        assertEquals(1, number(body(getAs("tech1", "/api/technicians/me/workload").andExpect(status().isOk())), "$.active"));
        postAs("tech1", "/api/incidents/" + id + "/resolve", "{\"resolution\": \"Restarted the gateway\"}").andExpect(status().isOk());

        assertEquals(0, number(body(getAs("tech1", "/api/technicians/me/workload").andExpect(status().isOk())), "$.active"));
    }

    @Test
    void onlyTechniciansHaveAWorkload() throws Exception {
        getAs("emp1", "/api/technicians/me/workload").andExpect(status().isForbidden());
        getAs("admin", "/api/technicians/me/workload").andExpect(status().isForbidden());
        getAs("manager", "/api/technicians/me/workload").andExpect(status().isForbidden());
        getAs(null, "/api/technicians/me/workload").andExpect(status().isUnauthorized());
    }
}
