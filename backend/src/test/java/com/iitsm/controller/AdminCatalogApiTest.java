package com.iitsm.controller;

import com.iitsm.support.AbstractApiTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Administration of IT services, categories, SLA times and the assignment strategy through the real security chain.
 * Seeded: services Corporate Email (3), Internal Wiki (1), Payment Application (5), VPN Gateway (4); nine categories
 * from Application to Storage; default SLA policies P1 5/30, P2 15/120, P3 60/480, P4 240/1440 minutes.
 */
class AdminCatalogApiTest extends AbstractApiTest {

    // ---------- helpers ----------

    private String serviceJson(String name, int criticality, String extra) {
        return "{\"name\": \"%s\", \"description\": \"Some description\", \"businessCriticality\": %d%s}".formatted(name, criticality, extra);
    }

    private String categoryJson(String name, String keywords, Long skillId, String extra) {
        return "{\"name\": \"%s\", \"description\": \"Some description\", \"keywords\": \"%s\", \"requiredSkillId\": %s%s}"
                .formatted(name, keywords, skillId, extra);
    }

    private List<String> serviceNames(String url) throws Exception {
        return list(body(getAs("admin", url).andExpect(status().isOk())), "$[*].name");
    }

    private long idByName(String url, String name) throws Exception {
        List<Map<String, Object>> all = list(body(getAs("admin", url).andExpect(status().isOk())), "$");
        return ((Number) all.stream().filter(item -> name.equals(item.get("name"))).findFirst().orElseThrow().get("id")).longValue();
    }

    private long skillId(String name) throws Exception {
        return idByName("/api/skills", name);
    }

    private long defaultPolicyId(String priority) throws Exception {
        List<Map<String, Object>> all = list(body(getAs("admin", "/api/admin/sla-policies").andExpect(status().isOk())), "$");
        return ((Number) all.stream().filter(p -> priority.equals(p.get("priority")) && p.get("service") == null)
                .findFirst().orElseThrow().get("id")).longValue();
    }

    private String strategy() throws Exception {
        return text(body(getAs("admin", "/api/admin/config/assignment-strategy").andExpect(status().isOk())), "$.strategy");
    }

    /** Reports the payment outage (a P1 on the Payment Application) and returns the incident as an administrator sees it. */
    private String newPaymentIncident() throws Exception {
        long id = submitIncident("emp1", "Payment application outage", "Users cannot pay",
                "Application", "Payment Application", 3, 3, 250);
        return body(getAs("admin", "/api/incidents/" + id).andExpect(status().isOk()));
    }

    private ResultActions call(String user, String method, String url, String json) throws Exception {
        return switch (method) {
            case "GET" -> getAs(user, url);
            case "POST" -> postAs(user, url, json);
            case "PUT" -> putAs(user, url, json);
            case "DELETE" -> deleteAs(user, url);
            default -> throw new IllegalArgumentException(method);
        };
    }

    // ---------- the lists everyone can read ----------

    @Test
    void anyLoggedInUserReadsTheActiveServicesAndCategoriesInNameOrder() throws Exception {
        for (String user : List.of("emp1", "tech1", "admin")) {
            assertEquals(List.of("Corporate Email", "Internal Wiki", "Payment Application", "VPN Gateway"),
                    list(body(getAs(user, "/api/services").andExpect(status().isOk())), "$[*].name"), user);
            assertEquals(List.of("Application", "Authentication", "Cloud", "Database", "Hardware", "Network", "Security",
                    "Software", "Storage"), list(body(getAs(user, "/api/categories").andExpect(status().isOk())), "$[*].name"), user);
        }
        getAs(null, "/api/services").andExpect(status().isUnauthorized());
        getAs(null, "/api/categories").andExpect(status().isUnauthorized());
    }

    // ---------- IT services ----------

    @Test
    void theSeededServicesAreListedWithTheirCriticality() throws Exception {
        String body = body(getAs("admin", "/api/admin/services").andExpect(status().isOk()));

        assertEquals(List.of("Corporate Email", "Internal Wiki", "Payment Application", "VPN Gateway"), list(body, "$[*].name"));
        assertEquals(List.of(3, 1, 5, 4), list(body, "$[*].businessCriticality"));
        assertEquals(List.of(true, true, true, true), list(body, "$[*].active"));
    }

    @Test
    void aNewServiceIsCreatedAndCanBeReportedAgainstAtOnce() throws Exception {
        String created = body(postAs("admin", "/api/admin/services", serviceJson("Intranet", 2, "")).andExpect(status().isCreated()));

        assertEquals("Intranet", text(created, "$.name"));
        assertEquals(2, number(created, "$.businessCriticality"));
        assertEquals(true, JsonPath.<Boolean>read(created, "$.active"));
        assertTrue(serviceNames("/api/services").contains("Intranet"));
        submitIncident("emp1", "Intranet down", "The portal does not load", "Application", "Intranet", 2, 2, 20);
    }

    @Test
    void aServiceCanBeCreatedSwitchedOff() throws Exception {
        postAs("admin", "/api/admin/services", serviceJson("Retired Tool", 1, ", \"active\": false")).andExpect(status().isCreated());

        assertFalse(serviceNames("/api/services").contains("Retired Tool"));
        assertTrue(serviceNames("/api/admin/services").contains("Retired Tool"));
    }

    @Test
    void aServiceNameCannotBeUsedTwiceAndTheDetailsAreChecked() throws Exception {
        postAs("admin", "/api/admin/services", serviceJson("Internal Wiki", 2, "")).andExpect(status().isConflict());
        postAs("admin", "/api/admin/services", serviceJson("internal WIKI", 2, "")).andExpect(status().isConflict());
        postAs("admin", "/api/admin/services", serviceJson(" ", 2, "")).andExpect(status().isBadRequest());
        postAs("admin", "/api/admin/services", serviceJson("Intranet", 0, "")).andExpect(status().isBadRequest());
        postAs("admin", "/api/admin/services", serviceJson("Intranet", 6, "")).andExpect(status().isBadRequest());
        postAs("admin", "/api/admin/services", "{\"name\": \"Intranet\"}").andExpect(status().isBadRequest());
        postAs("admin", "/api/admin/services", serviceJson("n".repeat(121), 2, "")).andExpect(status().isBadRequest());
        assertEquals(4, serviceNames("/api/admin/services").size());
    }

    @Test
    void aServiceCanBeChangedAndKeepsItsActiveStateWhenNoneIsGiven() throws Exception {
        long wiki = idByName("/api/admin/services", "Internal Wiki");

        String updated = body(putAs("admin", "/api/admin/services/" + wiki, serviceJson("Internal Wiki", 2, "")).andExpect(status().isOk()));

        assertEquals(2, number(updated, "$.businessCriticality"));
        assertEquals("Some description", text(updated, "$.description"));
        assertEquals(true, JsonPath.<Boolean>read(updated, "$.active"));
        putAs("admin", "/api/admin/services/" + wiki, serviceJson("internal wiki", 2, "")).andExpect(status().isOk());      // its own name, other capitals
        putAs("admin", "/api/admin/services/" + wiki, serviceJson("payment application", 2, "")).andExpect(status().isConflict());
        putAs("admin", "/api/admin/services/" + wiki, serviceJson("Internal Wiki", 9, "")).andExpect(status().isBadRequest());
        putAs("admin", "/api/admin/services/999999", serviceJson("Whatever", 2, "")).andExpect(status().isNotFound());
    }

    @Test
    void aSwitchedOffServiceDisappearsFromTheChoicesAndCannotBeReportedAgainstUntilSwitchedBackOn() throws Exception {
        long wiki = idByName("/api/admin/services", "Internal Wiki");
        String report = "{\"title\": \"t\", \"description\": \"d\", \"categoryId\": " + idByName("/api/categories", "Application")
                + ", \"serviceId\": " + wiki + ", \"impact\": 1, \"urgency\": 1, \"affectedUsers\": 1}";

        putAs("admin", "/api/admin/services/" + wiki, serviceJson("Internal Wiki", 1, ", \"active\": false")).andExpect(status().isOk());

        assertFalse(serviceNames("/api/services").contains("Internal Wiki"));
        assertTrue(serviceNames("/api/admin/services").contains("Internal Wiki"));
        postAs("emp1", "/api/incidents", report).andExpect(status().isNotFound());

        putAs("admin", "/api/admin/services/" + wiki, serviceJson("Internal Wiki", 1, ", \"active\": true")).andExpect(status().isOk());
        assertTrue(serviceNames("/api/services").contains("Internal Wiki"));
        postAs("emp1", "/api/incidents", report).andExpect(status().isCreated());
    }

    // ---------- categories ----------

    @Test
    void theSeededCategoriesShowTheirKeywordsAndRequiredSkill() throws Exception {
        String body = body(getAs("admin", "/api/admin/categories").andExpect(status().isOk()));

        assertEquals(List.of("Application", "Authentication", "Cloud", "Database", "Hardware", "Network", "Security",
                "Software", "Storage"), list(body, "$[*].name"));
        List<Map<String, Object>> all = list(body, "$");
        Map<String, Object> network = all.stream().filter(c -> "Network".equals(c.get("name"))).findFirst().orElseThrow();
        assertEquals("network,wifi,vpn,dns,latency,router,switch,firewall,timeout", network.get("keywords"));
        assertEquals("Networking", ((Map<?, ?>) network.get("requiredSkill")).get("name"));
    }

    @Test
    void aNewCategoryGetsTidyKeywordsAndItsSkillAndIsUsedForSuggestions() throws Exception {
        String created = body(postAs("admin", "/api/admin/categories",
                categoryJson("Telephony", "  phone , voip,,call ", skillId("Networking"), "")).andExpect(status().isCreated()));

        assertEquals("phone,voip,call", text(created, "$.keywords"));
        assertEquals("Networking", text(created, "$.requiredSkill.name"));
        assertEquals(true, JsonPath.<Boolean>read(created, "$.active"));
        assertTrue(serviceNames("/api/categories").contains("Telephony"));
        String suggested = body(postAs("emp1", "/api/incidents/suggest-category",
                "{\"title\": \"voip phone\", \"description\": \"call drops\"}").andExpect(status().isOk()));
        assertEquals("Telephony", text(suggested, "$[0].name"));
        assertEquals(3, number(suggested, "$[0].score"));
    }

    @Test
    void aCategoryNeedsNoSkill() throws Exception {
        String created = body(postAs("admin", "/api/admin/categories", categoryJson("Other", "misc", null, "")).andExpect(status().isCreated()));

        assertNull(JsonPath.read(created, "$.requiredSkill"));
    }

    @Test
    void aCategoryNameCannotBeUsedTwiceAndTheDetailsAreChecked() throws Exception {
        postAs("admin", "/api/admin/categories", categoryJson("Cloud", "x", null, "")).andExpect(status().isConflict());
        postAs("admin", "/api/admin/categories", categoryJson("cLoUd", "x", null, "")).andExpect(status().isConflict());
        postAs("admin", "/api/admin/categories", categoryJson(" ", "x", null, "")).andExpect(status().isBadRequest());
        postAs("admin", "/api/admin/categories", categoryJson("n".repeat(81), "x", null, "")).andExpect(status().isBadRequest());
        postAs("admin", "/api/admin/categories", categoryJson("Telephony", "x", 999999L, "")).andExpect(status().isNotFound());
        assertEquals(9, serviceNames("/api/admin/categories").size());
    }

    @Test
    void aCategoryCanBeChangedIncludingRemovingItsSkill() throws Exception {
        long network = idByName("/api/admin/categories", "Network");

        String updated = body(putAs("admin", "/api/admin/categories/" + network,
                categoryJson("Network", "wifi,lan", null, "")).andExpect(status().isOk()));

        assertEquals("wifi,lan", text(updated, "$.keywords"));
        assertNull(JsonPath.read(updated, "$.requiredSkill"));
        assertEquals(true, JsonPath.<Boolean>read(updated, "$.active"));
        putAs("admin", "/api/admin/categories/" + network, categoryJson("Network", "wifi,lan", skillId("Cloud"), "")).andExpect(status().isOk());
        putAs("admin", "/api/admin/categories/" + network, categoryJson("database", "x", null, "")).andExpect(status().isConflict());
        putAs("admin", "/api/admin/categories/" + network, categoryJson("Network", "x", 999999L, "")).andExpect(status().isNotFound());
        putAs("admin", "/api/admin/categories/999999", categoryJson("Whatever", "x", null, "")).andExpect(status().isNotFound());
    }

    @Test
    void aSwitchedOffCategoryDisappearsFromTheChoicesAndCannotBeUsedUntilSwitchedBackOn() throws Exception {
        long application = idByName("/api/admin/categories", "Application");
        String report = "{\"title\": \"t\", \"description\": \"d\", \"categoryId\": " + application
                + ", \"serviceId\": " + idByName("/api/services", "Payment Application")
                + ", \"impact\": 1, \"urgency\": 1, \"affectedUsers\": 1}";

        putAs("admin", "/api/admin/categories/" + application,
                categoryJson("Application", "application,app,payment", skillId("Application Support"), ", \"active\": false")).andExpect(status().isOk());

        assertFalse(serviceNames("/api/categories").contains("Application"));
        assertTrue(serviceNames("/api/admin/categories").contains("Application"));
        postAs("emp1", "/api/incidents", report).andExpect(status().isBadRequest());
        String suggested = body(postAs("emp1", "/api/incidents/suggest-category",
                "{\"title\": \"payment\", \"description\": \"transaction failing\"}").andExpect(status().isOk()));
        assertFalse(list(suggested, "$[*].name").contains("Application"));

        putAs("admin", "/api/admin/categories/" + application,
                categoryJson("Application", "application,app,payment", skillId("Application Support"), ", \"active\": true")).andExpect(status().isOk());
        postAs("emp1", "/api/incidents", report).andExpect(status().isCreated());
    }

    // ---------- SLA policies ----------

    @Test
    void theDefaultPoliciesAreListedFirstInPriorityOrder() throws Exception {
        String body = body(getAs("admin", "/api/admin/sla-policies").andExpect(status().isOk()));

        assertEquals(List.of("P1", "P2", "P3", "P4"), list(body, "$[*].priority"));
        assertEquals(List.of(5, 15, 60, 240), list(body, "$[*].responseMinutes"));
        assertEquals(List.of(30, 120, 480, 1440), list(body, "$[*].resolutionMinutes"));
        for (Object service : list(body, "$[*].service")) {
            assertNull(service);
        }
    }

    @Test
    void anOverrideForOneServiceIsAddedAndUsedForNewIncidentsOfThatPriority() throws Exception {
        long payment = idByName("/api/admin/services", "Payment Application");

        String created = body(postAs("admin", "/api/admin/sla-policies",
                "{\"priority\": \"P1\", \"serviceId\": " + payment + ", \"responseMinutes\": 2, \"resolutionMinutes\": 10}")
                .andExpect(status().isCreated()));

        assertEquals("P1", text(created, "$.priority"));
        assertEquals("Payment Application", text(created, "$.service.name"));
        List<String> priorities = list(body(getAs("admin", "/api/admin/sla-policies").andExpect(status().isOk())), "$[*].priority");
        assertEquals(List.of("P1", "P2", "P3", "P4", "P1"), priorities);                    // the override comes after the defaults
        String incident = newPaymentIncident();
        assertEquals("2026-11-10T09:02:00Z", text(incident, "$.responseDeadline"));
        assertEquals("2026-11-10T09:10:00Z", text(incident, "$.resolutionDeadline"));
    }

    @Test
    void aPriorityAndServicePairCanHaveOnlyOnePolicy() throws Exception {
        long payment = idByName("/api/admin/services", "Payment Application");
        String override = "{\"priority\": \"P1\", \"serviceId\": " + payment + ", \"responseMinutes\": 2, \"resolutionMinutes\": 10}";
        postAs("admin", "/api/admin/sla-policies", override).andExpect(status().isCreated());

        postAs("admin", "/api/admin/sla-policies", override).andExpect(status().isConflict());
        postAs("admin", "/api/admin/sla-policies", "{\"priority\": \"P1\", \"responseMinutes\": 1, \"resolutionMinutes\": 2}")
                .andExpect(status().isConflict());                                           // the default for P1 exists
        postAs("admin", "/api/admin/sla-policies",
                "{\"priority\": \"P2\", \"serviceId\": " + payment + ", \"responseMinutes\": 3, \"resolutionMinutes\": 9}")
                .andExpect(status().isCreated());                                            // another priority of the same service is fine
    }

    @Test
    void invalidNewPoliciesAreRejected() throws Exception {
        long payment = idByName("/api/admin/services", "Payment Application");

        postAs("admin", "/api/admin/sla-policies", "{\"priority\": \"P3\", \"serviceId\": 999999, \"responseMinutes\": 3, \"resolutionMinutes\": 9}")
                .andExpect(status().isNotFound());
        postAs("admin", "/api/admin/sla-policies", "{\"priority\": \"P3\", \"serviceId\": " + payment + ", \"responseMinutes\": 0, \"resolutionMinutes\": 9}")
                .andExpect(status().isBadRequest());
        postAs("admin", "/api/admin/sla-policies", "{\"priority\": \"P3\", \"serviceId\": " + payment + ", \"responseMinutes\": 3}")
                .andExpect(status().isBadRequest());
        postAs("admin", "/api/admin/sla-policies", "{\"serviceId\": " + payment + ", \"responseMinutes\": 3, \"resolutionMinutes\": 9}")
                .andExpect(status().isBadRequest());
        postAs("admin", "/api/admin/sla-policies", "{\"priority\": \"P9\", \"serviceId\": " + payment + ", \"responseMinutes\": 3, \"resolutionMinutes\": 9}")
                .andExpect(status().isBadRequest());
        assertEquals(4, list(body(getAs("admin", "/api/admin/sla-policies").andExpect(status().isOk())), "$").size());
    }

    @Test
    void changingADefaultPolicysTimesAffectsNewIncidentsAndKeepsItsPriority() throws Exception {
        long p1 = defaultPolicyId("P1");

        String updated = body(putAs("admin", "/api/admin/sla-policies/" + p1, "{\"responseMinutes\": 3, \"resolutionMinutes\": 20}")
                .andExpect(status().isOk()));

        assertEquals("P1", text(updated, "$.priority"));
        assertEquals(3, number(updated, "$.responseMinutes"));
        assertEquals(20, number(updated, "$.resolutionMinutes"));
        String incident = newPaymentIncident();
        assertEquals("2026-11-10T09:03:00Z", text(incident, "$.responseDeadline"));
        assertEquals("2026-11-10T09:20:00Z", text(incident, "$.resolutionDeadline"));
    }

    @Test
    void invalidPolicyChangesAreRejected() throws Exception {
        long p1 = defaultPolicyId("P1");

        putAs("admin", "/api/admin/sla-policies/" + p1, "{\"responseMinutes\": 0, \"resolutionMinutes\": 20}").andExpect(status().isBadRequest());
        putAs("admin", "/api/admin/sla-policies/" + p1, "{\"responseMinutes\": 3}").andExpect(status().isBadRequest());
        putAs("admin", "/api/admin/sla-policies/" + p1, "{}").andExpect(status().isBadRequest());
        putAs("admin", "/api/admin/sla-policies/999999", "{\"responseMinutes\": 3, \"resolutionMinutes\": 20}").andExpect(status().isNotFound());

        assertEquals(5, number(body(getAs("admin", "/api/admin/sla-policies").andExpect(status().isOk())), "$[0].responseMinutes"));
    }

    @Test
    void anOverrideCanBeDeletedAndTheDefaultAppliesAgain() throws Exception {
        long payment = idByName("/api/admin/services", "Payment Application");
        long overrideId = number(body(postAs("admin", "/api/admin/sla-policies",
                "{\"priority\": \"P1\", \"serviceId\": " + payment + ", \"responseMinutes\": 2, \"resolutionMinutes\": 10}")
                .andExpect(status().isCreated())), "$.id");

        deleteAs("admin", "/api/admin/sla-policies/" + overrideId).andExpect(status().isNoContent());

        assertEquals(4, list(body(getAs("admin", "/api/admin/sla-policies").andExpect(status().isOk())), "$").size());
        String incident = newPaymentIncident();
        assertEquals("2026-11-10T09:05:00Z", text(incident, "$.responseDeadline"));
        assertEquals("2026-11-10T09:30:00Z", text(incident, "$.resolutionDeadline"));
    }

    @Test
    void aDefaultPolicyCannotBeDeletedAndAnUnknownOneIs404() throws Exception {
        deleteAs("admin", "/api/admin/sla-policies/" + defaultPolicyId("P1")).andExpect(status().isBadRequest());
        deleteAs("admin", "/api/admin/sla-policies/999999").andExpect(status().isNotFound());
        deleteAs("admin", "/api/admin/sla-policies/abc").andExpect(status().isBadRequest());

        assertEquals(4, list(body(getAs("admin", "/api/admin/sla-policies").andExpect(status().isOk())), "$").size());
        assertEquals("2026-11-10T09:05:00Z", text(newPaymentIncident(), "$.responseDeadline"));            // incidents still get their SLA
    }

    // ---------- the assignment strategy ----------

    @Test
    void theStrategyInUseAndTheAvailableNamesAreShown() throws Exception {
        String body = body(getAs("admin", "/api/admin/config/assignment-strategy").andExpect(status().isOk()));

        assertEquals("SKILL_BASED", text(body, "$.strategy"));
        assertEquals(List.of("LOAD_BASED", "ROUND_ROBIN", "SKILL_BASED"), list(body, "$.available"));
    }

    @Test
    void everyAvailableStrategyCanBeChosen() throws Exception {
        for (String name : List.of("LOAD_BASED", "ROUND_ROBIN", "SKILL_BASED")) {
            String body = body(putAs("admin", "/api/admin/config/assignment-strategy", "{\"strategy\": \"" + name + "\"}")
                    .andExpect(status().isOk()));
            assertEquals(name, text(body, "$.strategy"));
            assertEquals(name, strategy());
        }
    }

    @Test
    void anUnknownStrategyIsA400AndTheOldOneStays() throws Exception {
        putAs("admin", "/api/admin/config/assignment-strategy", "{\"strategy\": \"LOAD_BASED\"}").andExpect(status().isOk());

        for (String bad : List.of("FASTEST", "load_based", "", " ")) {
            putAs("admin", "/api/admin/config/assignment-strategy", "{\"strategy\": \"" + bad + "\"}").andExpect(status().isBadRequest());
        }
        putAs("admin", "/api/admin/config/assignment-strategy", "{}").andExpect(status().isBadRequest());
        putAs("admin", "/api/admin/config/assignment-strategy", "{\"strategy\": 5}").andExpect(status().isBadRequest());

        assertEquals("LOAD_BASED", strategy());
    }

    @Test
    void switchingTheStrategyChangesWhoGetsTheNextIncident() throws Exception {
        assertEquals("Kiran Tech", text(newPaymentIncident(), "$.assignee.name"));       // skill based: the best Application Support skill

        putAs("admin", "/api/admin/config/assignment-strategy", "{\"strategy\": \"LOAD_BASED\"}").andExpect(status().isOk());
        assertEquals("Dev Tech", text(newPaymentIncident(), "$.assignee.name"));         // least busy, then the most experienced

        putAs("admin", "/api/admin/config/assignment-strategy", "{\"strategy\": \"SKILL_BASED\"}").andExpect(status().isOk());
        assertEquals("Kiran Tech", text(newPaymentIncident(), "$.assignee.name"));
    }

    // ---------- administrators only ----------

    @Test
    void everyAdministrationAddressRefusesEveryoneElseAndChangesNothing() throws Exception {
        long wiki = idByName("/api/admin/services", "Internal Wiki");
        long cloud = idByName("/api/admin/categories", "Cloud");
        long p1 = defaultPolicyId("P1");
        List<String[]> requests = List.of(
                new String[]{"GET", "/api/admin/services", ""},
                new String[]{"POST", "/api/admin/services", serviceJson("Intruder", 1, "")},
                new String[]{"PUT", "/api/admin/services/" + wiki, serviceJson("Hacked", 1, ", \"active\": false")},
                new String[]{"GET", "/api/admin/categories", ""},
                new String[]{"POST", "/api/admin/categories", categoryJson("Intruder", "x", null, "")},
                new String[]{"PUT", "/api/admin/categories/" + cloud, categoryJson("Hacked", "x", null, ", \"active\": false")},
                new String[]{"GET", "/api/admin/sla-policies", ""},
                new String[]{"POST", "/api/admin/sla-policies", "{\"priority\": \"P3\", \"serviceId\": " + wiki + ", \"responseMinutes\": 1, \"resolutionMinutes\": 2}"},
                new String[]{"PUT", "/api/admin/sla-policies/" + p1, "{\"responseMinutes\": 1, \"resolutionMinutes\": 2}"},
                new String[]{"DELETE", "/api/admin/sla-policies/" + p1, ""},
                new String[]{"GET", "/api/admin/config/assignment-strategy", ""},
                new String[]{"PUT", "/api/admin/config/assignment-strategy", "{\"strategy\": \"LOAD_BASED\"}"});

        for (String user : List.of("emp1", "tech1", "lead.app")) {
            for (String[] request : requests) {
                call(user, request[0], request[1], request[2]).andExpect(status().isForbidden());
            }
        }
        for (String[] request : requests) {
            call(null, request[0], request[1], request[2]).andExpect(status().isUnauthorized());
        }
        call("manager", "GET", "/api/admin/sla-policies", "").andExpect(status().isOk());      // an IT manager counts as an administrator

        assertEquals(List.of("Corporate Email", "Internal Wiki", "Payment Application", "VPN Gateway"), serviceNames("/api/admin/services"));
        assertEquals(9, serviceNames("/api/admin/categories").size());
        assertEquals(4, list(body(getAs("admin", "/api/admin/sla-policies").andExpect(status().isOk())), "$").size());
        assertEquals("SKILL_BASED", strategy());
        assertTrue(serviceNames("/api/services").contains("Internal Wiki"));                    // nothing was switched off
        assertTrue(serviceNames("/api/categories").contains("Cloud"));
    }
}
