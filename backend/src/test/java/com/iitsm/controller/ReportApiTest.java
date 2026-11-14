package com.iitsm.controller;

import com.iitsm.repository.FeedbackRepository;
import com.iitsm.service.SlaMonitorService;
import com.iitsm.support.AbstractApiTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The report endpoints through the real security chain, with exact numbers.
 * <p>
 * Other tests may have left incidents in the database, so every report is read once BEFORE the test data is
 * created, and the checks are "before + what this test added". The same goes for the SLA states: the monitor is run
 * once before the baseline so that older incidents settle first. Feedback is emptied (inside the test's own
 * transaction, which is rolled back) so the average rating is exact.
 * <p>
 * Test data, all reported on 10 November at 09:00 UTC (the seeded strategy is SKILL_BASED):
 * <pre>
 * id  reporter service             category     impact urgency users  priority  assigned   then
 *  A  emp1     Payment Application Application  3      3       250    P1 (45)   Kiran      resolved, verified, rated 5: CLOSED
 *  B  emp2     VPN Gateway         Network      2      2       5      P2 (16)   Sana       resolved: RESOLVED
 *  C  emp1     Corporate Email     Database     1      3       10     P3 (9)    Dev        cancelled by the admin: CANCELLED
 *  D  emp2     Internal Wiki       Application  1      1       1      P4 (1)    Kiran      stays ASSIGNED
 *  E  emp1     Corporate Email     Cloud        1      2       100    P3 bumped to P2 (6)  Sana  stays ASSIGNED
 *  F  emp2     Payment Application Application  3      3       250    P1 (45)   Kiran      stays ASSIGNED
 *  G  emp2     Internal Wiki       Application  2      1       1      P4 (2)    Kiran      resolved, verified, rated 4: CLOSED
 * </pre>
 * Then the clock moves 13 minutes and the SLA monitor runs: F (P1, not acknowledged after 5 minutes) is VIOLATED,
 * E (P2, not acknowledged, 13 of 15 minutes) is AT_RISK, and the rest are ON_TRACK or no longer open.
 */
class ReportApiTest extends AbstractApiTest {

    private static final List<String> REPORTS = List.of("overview", "technician-workload", "service-analysis",
            "category-distribution", "priority-distribution", "sla-compliance", "incidents-per-day");
    private static final String TODAY = "2026-11-10";

    @Autowired SlaMonitorService monitor;
    @Autowired FeedbackRepository feedbackRepository;

    /** Every report as it was before this test added its incidents. */
    private final Map<String, String> before = new HashMap<>();

    @BeforeEach
    void createTestData() throws Exception {
        feedbackRepository.deleteAll();
        feedbackRepository.flush();

        clock.advance(Duration.ofMinutes(13));
        monitor.runOnce();                                  // older incidents reach their SLA state first
        for (String name : REPORTS) {
            before.put(name, report(name));
        }
        clock.reset();

        long a = submitIncident("emp1", "Payment application outage", "Users cannot pay",
                "Application", "Payment Application", 3, 3, 250);
        long b = submitIncident("emp2", "VPN gateway down", "Nobody can connect",
                "Network", "VPN Gateway", 2, 2, 5);
        long c = submitIncident("emp1", "Mailbox database slow", "Mail search is slow",
                "Database", "Corporate Email", 1, 3, 10);
        submitIncident("emp2", "Wiki page broken", "The page layout is broken",
                "Application", "Internal Wiki", 1, 1, 1);
        submitIncident("emp1", "Mail instance restarts", "The instance restarts often",
                "Cloud", "Corporate Email", 1, 2, 100);
        submitIncident("emp2", "Payment application unavailable", "Payments fail",
                "Application", "Payment Application", 3, 3, 250);
        long g = submitIncident("emp2", "Wiki search not working", "Search returns nothing",
                "Application", "Internal Wiki", 2, 1, 1);

        closeWithRating(a, "tech1", "emp1", 5);
        resolve(b, "tech2");
        postAs("admin", "/api/incidents/" + c + "/cancel").andExpect(status().isOk());
        closeWithRating(g, "tech1", "emp2", 4);

        clock.advance(Duration.ofMinutes(13));
        monitor.runOnce();
    }

    // ------------------------------------------------------------------ helpers

    private void resolve(long id, String technician) throws Exception {
        postAs(technician, "/api/incidents/" + id + "/acknowledge").andExpect(status().isOk());
        postAs(technician, "/api/incidents/" + id + "/start").andExpect(status().isOk());
        postAs(technician, "/api/incidents/" + id + "/resolve", "{\"resolution\": \"Fixed\"}").andExpect(status().isOk());
    }

    /** Resolve, verify and rate: the rating closes the incident. */
    private void closeWithRating(long id, String technician, String reporter, int rating) throws Exception {
        resolve(id, technician);
        postAs(reporter, "/api/incidents/" + id + "/verify").andExpect(status().isOk());
        postAs(reporter, "/api/incidents/" + id + "/feedback", "{\"rating\": %d, \"comments\": \"ok\"}".formatted(rating))
                .andExpect(status().isCreated());
    }

    private String report(String name) throws Exception {
        return body(getAs("admin", "/api/reports/" + name).andExpect(status().isOk()));
    }

    /** A number of one line of a list report: the line whose {@code key} is {@code value}; 0 when there is no such line. */
    private long valueOf(String json, String key, String value, String field) {
        List<Number> found = list(json, "$[?(@.%s == '%s')].%s".formatted(key, value, field));
        return found.isEmpty() ? 0 : found.get(0).longValue();
    }

    /** How much a number of one line of a list report grew since before the test data was created. */
    private long added(String report, String key, String value, String field) throws Exception {
        return valueOf(report(report), key, value, field) - valueOf(before.get(report), key, value, field);
    }

    private long addedToOverview(String field) throws Exception {
        return number(report("overview"), "$." + field) - number(before.get("overview"), "$." + field);
    }

    private List<Long> counts(String json) {
        List<Number> numbers = list(json, "$[*].count");
        return numbers.stream().map(Number::longValue).toList();
    }

    // ------------------------------------------------------------------ overview

    @Test
    void theOverviewCountsEveryIncident() throws Exception {
        assertEquals(7, addedToOverview("total"));
        assertEquals(4, addedToOverview("open"));            // B (resolved but not closed), D, E, F
        assertEquals(1, addedToOverview("critical"));        // F: the only open P1 (A is closed)
        assertEquals(3, addedToOverview("resolved"));        // A and G closed, B resolved
        assertEquals(1, addedToOverview("slaViolations"));   // F
    }

    @Test
    void theAverageRatingIsEmptyWithoutFeedbackAndTheMeanAfterwards() throws Exception {
        assertNull(JsonPath.read(before.get("overview"), "$.averageRating"));

        double average = ((Number) JsonPath.read(report("overview"), "$.averageRating")).doubleValue();
        assertEquals(4.5, average, 0.0001);                  // ratings 5 and 4
    }

    // ------------------------------------------------------------------ technician workload

    @Test
    void eachTechnicianShowsTheirActiveIncidentsAndLimit() throws Exception {
        String now = report("technician-workload");

        assertEquals(2, added("technician-workload", "name", "Kiran Tech", "active"));   // D and F (A and G are closed)
        assertEquals(1, added("technician-workload", "name", "Sana Tech", "active"));    // E (B is resolved)
        assertEquals(0, added("technician-workload", "name", "Dev Tech", "active"));     // C was cancelled
        assertEquals(0, added("technician-workload", "name", "Meera Lead", "active"));

        assertEquals(5, valueOf(now, "name", "Kiran Tech", "max"));
        assertEquals(5, valueOf(now, "name", "Sana Tech", "max"));
        assertEquals(3, valueOf(now, "name", "Dev Tech", "max"));
        assertEquals(5, valueOf(now, "name", "Meera Lead", "max"));                       // team leads are technicians too
        assertEquals(user("tech1").getId().longValue(), valueOf(now, "name", "Kiran Tech", "technicianId"));
    }

    @Test
    void techniciansAreListedByNameAndDeactivatedAccountsAreLeftOut() throws Exception {
        List<String> names = list(report("technician-workload"), "$[*].name");
        assertTrue(names.indexOf("Dev Tech") < names.indexOf("Kiran Tech"));
        assertTrue(names.indexOf("Kiran Tech") < names.indexOf("Meera Lead"));
        assertTrue(names.indexOf("Meera Lead") < names.indexOf("Sana Tech"));
        assertFalse(names.contains("Asha Employee"));
        assertFalse(names.contains("System Admin"));

        putAs("admin", "/api/admin/users/" + user("tech3").getId(), "{\"fullName\": \"Dev Tech\", \"active\": false}")
                .andExpect(status().isOk());

        assertFalse(list(report("technician-workload"), "$[*].name").contains("Dev Tech"));
    }

    // ------------------------------------------------------------------ service, category, priority, SLA

    @Test
    void serviceAnalysisCountsIncidentsPerServiceMostFirst() throws Exception {
        assertEquals(2, added("service-analysis", "name", "Payment Application", "count"));   // A, F
        assertEquals(1, added("service-analysis", "name", "VPN Gateway", "count"));           // B
        assertEquals(2, added("service-analysis", "name", "Corporate Email", "count"));       // C, E
        assertEquals(2, added("service-analysis", "name", "Internal Wiki", "count"));         // D, G

        List<Long> counts = counts(report("service-analysis"));
        for (int i = 0; i + 1 < counts.size(); i++) {
            assertTrue(counts.get(i) >= counts.get(i + 1), "the service with most incidents comes first");
        }
        assertNotNull(text(report("service-analysis"), "$[0].name"));
        assertTrue(number(report("service-analysis"), "$[0].id") > 0);
    }

    @Test
    void categoryDistributionCountsIncidentsPerCategory() throws Exception {
        assertEquals(4, added("category-distribution", "name", "Application", "count"));      // A, D, F, G
        assertEquals(1, added("category-distribution", "name", "Network", "count"));          // B
        assertEquals(1, added("category-distribution", "name", "Database", "count"));         // C
        assertEquals(1, added("category-distribution", "name", "Cloud", "count"));            // E
        assertEquals(0, added("category-distribution", "name", "Security", "count"));
    }

    @Test
    void priorityDistributionListsAllFourPriorities() throws Exception {
        assertEquals(List.of("P1", "P2", "P3", "P4"), list(report("priority-distribution"), "$[*].priority"));

        assertEquals(2, added("priority-distribution", "priority", "P1", "count"));           // A, F
        assertEquals(2, added("priority-distribution", "priority", "P2", "count"));           // B, and E after the bump
        assertEquals(1, added("priority-distribution", "priority", "P3", "count"));           // C
        assertEquals(2, added("priority-distribution", "priority", "P4", "count"));           // D, G
    }

    @Test
    void slaComplianceListsAllThreeStates() throws Exception {
        assertEquals(List.of("ON_TRACK", "AT_RISK", "VIOLATED"), list(report("sla-compliance"), "$[*].slaStatus"));

        assertEquals(1, added("sla-compliance", "slaStatus", "VIOLATED", "count"));           // F
        assertEquals(1, added("sla-compliance", "slaStatus", "AT_RISK", "count"));            // E
        assertEquals(5, added("sla-compliance", "slaStatus", "ON_TRACK", "count"));           // A, B, C, D, G
    }

    // ------------------------------------------------------------------ incidents per day

    @Test
    void incidentsPerDayAreZeroFilledAndOldestFirst() throws Exception {
        clock.set(Instant.parse("2026-11-08T10:00:00Z"));                // two more incidents, two days earlier
        submitIncident("emp2", "Mail database locked", "Queries wait forever", "Database", "Corporate Email", 1, 1, 1);
        submitIncident("emp2", "Mail database slow again", "Queries are slow", "Database", "Corporate Email", 1, 1, 1);
        clock.set(Instant.parse("2026-11-10T09:13:00Z"));

        String now = report("incidents-per-day");                         // 14 days by default

        List<String> expectedDates = new ArrayList<>();
        for (int i = 13; i >= 0; i--) {
            expectedDates.add(LocalDate.parse(TODAY).minusDays(i).toString());
        }
        assertEquals(expectedDates, list(now, "$[*].date"));

        List<Long> baseline = counts(before.get("incidents-per-day"));
        List<Long> expected = new ArrayList<>();
        for (int i = 0; i < expectedDates.size(); i++) {
            long extra = switch (expectedDates.get(i)) {
                case "2026-11-08" -> 2;                                   // the two incidents of this test
                case "2026-11-10" -> 7;                                   // A to G
                default -> 0;
            };
            expected.add(baseline.get(i) + extra);
        }
        assertEquals(expected, counts(now));
    }

    @Test
    void theNumberOfDaysCanBeChosen() throws Exception {
        String one = body(getAs("admin", "/api/reports/incidents-per-day?days=1").andExpect(status().isOk()));
        assertEquals(List.of(TODAY), list(one, "$[*].date"));
        List<Long> baseline = counts(before.get("incidents-per-day"));
        assertEquals(baseline.get(baseline.size() - 1) + 7, counts(one).get(0));

        String three = body(getAs("admin", "/api/reports/incidents-per-day?days=3").andExpect(status().isOk()));
        assertEquals(List.of("2026-11-08", "2026-11-09", "2026-11-10"), list(three, "$[*].date"));

        String ninety = body(getAs("admin", "/api/reports/incidents-per-day?days=90").andExpect(status().isOk()));
        assertEquals(90, list(ninety, "$[*].date").size());
    }

    @Test
    void aBadNumberOfDaysIsA400() throws Exception {
        for (String days : List.of("0", "-1", "91", "abc")) {
            getAs("admin", "/api/reports/incidents-per-day?days=" + days).andExpect(status().isBadRequest());
        }
    }

    // ------------------------------------------------------------------ administrators only

    @Test
    void anITManagerIsAnAdministratorToo() throws Exception {
        for (String name : REPORTS) {
            getAs("manager", "/api/reports/" + name).andExpect(status().isOk());
        }
    }

    @Test
    void everyoneElseIsForbiddenAndNoLoginIs401() throws Exception {
        for (String name : REPORTS) {
            for (String user : List.of("emp1", "emp2", "tech1", "tech2", "lead.app")) {
                getAs(user, "/api/reports/" + name).andExpect(status().isForbidden());
            }
            getAs(null, "/api/reports/" + name).andExpect(status().isUnauthorized());
        }
    }
}
