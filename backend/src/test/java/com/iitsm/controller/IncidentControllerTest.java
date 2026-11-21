package com.iitsm.controller;

import com.iitsm.repository.CategoryRepository;
import com.iitsm.repository.ITServiceRepository;
import com.iitsm.support.AbstractApiTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The incident endpoints through the real security chain with real tokens for an employee (emp1, emp2),
 * a technician (tech1, tech2) and an administrator. The story is demo workflow 1: emp1 reports a payment outage,
 * which is assigned to tech1 automatically.
 */
class IncidentControllerTest extends AbstractApiTest {

    @Autowired CategoryRepository categories;
    @Autowired ITServiceRepository services;

    // ---------- helpers ----------

    private String reportJson(String title, String description) {
        return """
                {"title": "%s", "description": "%s", "categoryId": %d, "serviceId": %d,
                 "impact": 3, "urgency": 3, "affectedUsers": 250}
                """.formatted(title, description,
                categories.findByName("Application").orElseThrow().getId(),
                services.findByName("Payment Application").orElseThrow().getId());
    }

    /** emp1 reports the payment outage; returns its id. */
    private long report() throws Exception {
        String response = body(postAs("emp1", "/api/incidents",
                reportJson("Payment application outage", "Users cannot complete payment transactions"))
                .andExpect(status().isCreated()));
        return ((Number) JsonPath.read(response, "$.incident.id")).longValue();
    }

    private String url(long id, String action) {
        return "/api/incidents/" + id + (action.isEmpty() ? "" : "/" + action);
    }

    private String field(String json, String path) {
        return JsonPath.read(json, path);
    }

    private String statusOf(long id) throws Exception {
        return field(body(getAs("admin", url(id, "")).andExpect(status().isOk())), "$.status");
    }

    /** tech1 acknowledges and starts working. */
    private void startWork(long id) throws Exception {
        postAs("tech1", url(id, "acknowledge")).andExpect(status().isOk());
        postAs("tech1", url(id, "start")).andExpect(status().isOk());
    }

    // ---------- reporting ----------

    @Test
    void anEmployeeReportsAnIncidentAndGetsItBackClassifiedAndAssigned() throws Exception {
        String response = body(postAs("emp1", "/api/incidents",
                reportJson("Payment application outage", "Users cannot complete payment transactions"))
                .andExpect(status().isCreated()));

        assertTrue(field(response, "$.incident.incidentNumber").matches("INC\\d+"));
        assertEquals("P1", field(response, "$.incident.priority"));
        assertEquals("ASSIGNED", field(response, "$.incident.status"));
        assertEquals("ON_TRACK", field(response, "$.incident.slaStatus"));
        assertEquals("Asha Employee", field(response, "$.incident.reporter.name"));
        assertEquals("Payment Application", field(response, "$.incident.service.name"));
        assertEquals("Application", field(response, "$.incident.category.name"));
        assertEquals("Kiran Tech", field(response, "$.incident.assignee.name"));      // tech1: the best skill match
        List<Object> duplicates = JsonPath.read(response, "$.possibleDuplicates");
        assertTrue(duplicates.isEmpty());
    }

    @Test
    void timesAreIsoStrings() throws Exception {
        String response = body(postAs("emp1", "/api/incidents",
                reportJson("Payment application outage", "Users cannot complete payment transactions"))
                .andExpect(status().isCreated()));

        assertTrue(field(response, "$.incident.createdAt").matches("\\d{4}-\\d{2}-\\d{2}T.*Z"), field(response, "$.incident.createdAt"));
        assertTrue(field(response, "$.incident.resolutionDeadline").matches("\\d{4}-\\d{2}-\\d{2}T.*Z"));
    }

    @Test
    void aSimilarSecondReportShowsTheFullDuplicateToItsOwnerAndOnlyTheNumberToAnotherEmployee() throws Exception {
        long first = report();   // by emp1
        String firstNumber = field(body(getAs("admin", "/api/incidents/" + first).andExpect(status().isOk())), "$.incidentNumber");

        String byOther = body(postAs("emp2", "/api/incidents",
                reportJson("Payment application down", "Users cannot complete payment transactions"))
                .andExpect(status().isCreated()));
        List<String> numbers = JsonPath.read(byOther, "$.possibleDuplicates[*].number");
        assertEquals(List.of(firstNumber), numbers, "the other employee is told that it is already reported");
        assertNull(JsonPath.read(byOther, "$.possibleDuplicates[0].title"), "but not what it says");
        assertNull(JsonPath.read(byOther, "$.possibleDuplicates[0].id"), "and gets no way to open it");

        String bySame = body(postAs("emp1", "/api/incidents",
                reportJson("Payment application down", "Users cannot complete payment transactions"))
                .andExpect(status().isCreated()));
        List<String> titles = JsonPath.read(bySame, "$.possibleDuplicates[*].title");
        assertEquals(List.of("Payment application outage"), titles);
    }

    @Test
    void invalidReportsAreRejectedWithTheFieldsThatAreWrong() throws Exception {
        String blankTitle = body(postAs("emp1", "/api/incidents", """
                {"title": " ", "description": "d", "categoryId": 1, "serviceId": 1, "impact": 3, "urgency": 3, "affectedUsers": 5}
                """).andExpect(status().isBadRequest()));
        assertNotNull(field(blankTitle, "$.fieldErrors.title"));

        String badLevels = body(postAs("emp1", "/api/incidents", """
                {"title": "t", "description": "d", "categoryId": 1, "serviceId": 1, "impact": 4, "urgency": 0, "affectedUsers": 0}
                """).andExpect(status().isBadRequest()));
        assertNotNull(field(badLevels, "$.fieldErrors.impact"));
        assertNotNull(field(badLevels, "$.fieldErrors.urgency"));
        assertNotNull(field(badLevels, "$.fieldErrors.affectedUsers"));

        String missingIds = body(postAs("emp1", "/api/incidents", """
                {"title": "t", "description": "d", "impact": 3, "urgency": 3, "affectedUsers": 5}
                """).andExpect(status().isBadRequest()));
        assertNotNull(field(missingIds, "$.fieldErrors.categoryId"));
        assertNotNull(field(missingIds, "$.fieldErrors.serviceId"));
    }

    @Test
    void aBodyThatIsNotValidJsonIsA400() throws Exception {
        postAs("emp1", "/api/incidents", "{ this is not json").andExpect(status().isBadRequest());
        postAs("emp1", "/api/incidents", "").andExpect(status().isBadRequest());
    }

    @Test
    void anUnknownServiceIs404AndAnUnknownCategoryIs400() throws Exception {
        long category = categories.findByName("Application").orElseThrow().getId();
        long service = services.findByName("Payment Application").orElseThrow().getId();

        postAs("emp1", "/api/incidents", """
                {"title": "t", "description": "d", "categoryId": %d, "serviceId": 999999, "impact": 3, "urgency": 3, "affectedUsers": 5}
                """.formatted(category)).andExpect(status().isNotFound());
        postAs("emp1", "/api/incidents", """
                {"title": "t", "description": "d", "categoryId": 999999, "serviceId": %d, "impact": 3, "urgency": 3, "affectedUsers": 5}
                """.formatted(service)).andExpect(status().isBadRequest());
    }

    // ---------- who is logged in ----------

    @Test
    void noTokenOrABadTokenIs401() throws Exception {
        postAs(null, "/api/incidents", reportJson("t", "d")).andExpect(status().isUnauthorized());
        getAs(null, "/api/incidents/1").andExpect(status().isUnauthorized());
        send(null, get("/api/incidents/1").header("Authorization", "Bearer not-a-real-token")).andExpect(status().isUnauthorized());
    }

    // ---------- reading one incident ----------

    @Test
    void theReporterTheAssigneeAndAnAdminCanReadItButAnotherEmployeeCannot() throws Exception {
        long id = report();

        getAs("emp1", url(id, "")).andExpect(status().isOk());
        getAs("tech1", url(id, "")).andExpect(status().isOk());
        getAs("admin", url(id, "")).andExpect(status().isOk());
        getAs("emp2", url(id, "")).andExpect(status().isForbidden());
        getAs("tech2", url(id, "")).andExpect(status().isForbidden());
    }

    @Test
    void anIncidentThatDoesNotExistIs404AndANonNumericIdIs400() throws Exception {
        getAs("admin", "/api/incidents/999999").andExpect(status().isNotFound());
        getAs("admin", "/api/incidents/abc").andExpect(status().isBadRequest());
    }

    @Test
    void anUnknownAddressIs404AndAWrongMethodIs405() throws Exception {
        getAs("admin", "/api/nothing-here").andExpect(status().isNotFound());
        send("admin", delete("/api/incidents/1"))
                .andExpect(status().isMethodNotAllowed());
    }

    // ---------- the technician's part ----------

    @Test
    void theAssigneeAcknowledgesStartsAndResolves() throws Exception {
        long id = report();

        String acknowledged = body(postAs("tech1", url(id, "acknowledge")).andExpect(status().isOk()));
        assertEquals("ACKNOWLEDGED", field(acknowledged, "$.status"));
        assertNotNull(field(acknowledged, "$.respondedAt"));
        assertEquals("IN_PROGRESS", field(body(postAs("tech1", url(id, "start")).andExpect(status().isOk())), "$.status"));
        String resolved = body(postAs("tech1", url(id, "resolve"), """
                {"resolution": "Restarted the payment gateway"}""").andExpect(status().isOk()));
        assertEquals("RESOLVED", field(resolved, "$.status"));
        assertEquals("Restarted the payment gateway", field(resolved, "$.resolution"));
    }

    @Test
    void someoneElseCannotDoTheAssigneesWork() throws Exception {
        long id = report();

        postAs("tech2", url(id, "acknowledge")).andExpect(status().isForbidden());
        postAs("emp1", url(id, "acknowledge")).andExpect(status().isForbidden());
        postAs("admin", url(id, "acknowledge")).andExpect(status().isForbidden());

        assertEquals("ASSIGNED", statusOf(id));
    }

    @Test
    void anActionThatIsNotAllowedInTheCurrentStatusIs409() throws Exception {
        long id = report();
        postAs("tech1", url(id, "acknowledge")).andExpect(status().isOk());

        postAs("tech1", url(id, "acknowledge")).andExpect(status().isConflict());
        postAs("tech1", url(id, "resolve"), """
                {"resolution": "too early"}""").andExpect(status().isConflict());

        assertEquals("ACKNOWLEDGED", statusOf(id));
    }

    @Test
    void aBlankResolutionOrReopenReasonIs400() throws Exception {
        long id = report();
        startWork(id);

        postAs("tech1", url(id, "resolve"), """
                {"resolution": " "}""").andExpect(status().isBadRequest());
        postAs("tech1", url(id, "resolve"), "{}").andExpect(status().isBadRequest());
        postAs("tech1", url(id, "resolve"), """
                {"resolution": "Restarted"}""").andExpect(status().isOk());
        postAs("emp1", url(id, "reopen"), """
                {"reason": ""}""").andExpect(status().isBadRequest());

        assertEquals("RESOLVED", statusOf(id));
    }

    // ---------- the reporter's part: verify, reopen, cancel, feedback, history ----------

    @Test
    void theReporterVerifiesGivesFeedbackAndTheIncidentClosesWithAFullHistory() throws Exception {
        long id = report();
        startWork(id);
        postAs("tech1", url(id, "resolve"), """
                {"resolution": "Restarted the payment gateway"}""").andExpect(status().isOk());

        postAs("tech1", url(id, "verify")).andExpect(status().isForbidden());           // only the reporter verifies
        assertEquals("VERIFIED", field(body(postAs("emp1", url(id, "verify")).andExpect(status().isOk())), "$.status"));

        getAs("emp1", url(id, "feedback")).andExpect(status().isNotFound());              // none yet
        postAs("tech1", url(id, "feedback"), """
                {"rating": 5, "comments": "x"}""").andExpect(status().isForbidden());
        postAs("emp1", url(id, "feedback"), """
                {"rating": 6, "comments": "x"}""").andExpect(status().isBadRequest());
        String feedback = body(postAs("emp1", url(id, "feedback"), """
                {"rating": 5, "comments": "Fast and friendly"}""").andExpect(status().isCreated()));
        assertEquals("Fast and friendly", field(feedback, "$.comments"));
        postAs("emp1", url(id, "feedback"), """
                {"rating": 1, "comments": "again"}""").andExpect(status().isBadRequest());   // once only

        assertEquals("CLOSED", statusOf(id));
        Integer rating = JsonPath.read(body(getAs("emp1", url(id, "feedback")).andExpect(status().isOk())), "$.rating");
        assertEquals(5, rating.intValue());

        String history = body(getAs("emp1", url(id, "history")).andExpect(status().isOk()));
        List<String> actions = JsonPath.read(history, "$[*].action");
        assertEquals(List.of("CREATED", "CLASSIFIED", "ASSIGNED", "ACKNOWLEDGED", "INVESTIGATION_STARTED",
                "RESOLVED", "VERIFIED", "CLOSED"), actions);
        assertNull(JsonPath.read(history, "$[7].user"));                                   // closed by the system
        getAs("emp2", url(id, "history")).andExpect(status().isForbidden());
    }

    @Test
    void theReporterReopensAFixThatDidNotWork() throws Exception {
        long id = report();
        startWork(id);
        postAs("tech1", url(id, "resolve"), """
                {"resolution": "Restarted"}""").andExpect(status().isOk());

        String reopened = body(postAs("emp1", url(id, "reopen"), """
                {"reason": "Still failing for some users"}""").andExpect(status().isOk()));

        assertEquals("IN_PROGRESS", field(reopened, "$.status"));
    }

    @Test
    void theReporterOrAnAdminCancelsBeforeWorkStartsButNotAnyoneElse() throws Exception {
        long mine = report();
        postAs("emp2", url(mine, "cancel")).andExpect(status().isForbidden());
        postAs("tech2", url(mine, "cancel")).andExpect(status().isForbidden());
        assertEquals("CANCELLED", field(body(postAs("emp1", url(mine, "cancel")).andExpect(status().isOk())), "$.status"));

        long other = report();
        assertEquals("CANCELLED", field(body(postAs("admin", url(other, "cancel")).andExpect(status().isOk())), "$.status"));
    }

    // ---------- administrators only ----------

    @Test
    void theAdminOnlyAddressesAreForbiddenToEveryoneElse() throws Exception {
        long id = report();
        long other = report();
        long tech2 = user("tech2").getId();

        for (String name : List.of("emp1", "tech1")) {
            postAs(name, url(id, "close")).andExpect(status().isForbidden());
            postAs(name, url(id, "duplicate"), "{\"originalId\": " + other + "}").andExpect(status().isForbidden());
            postAs(name, url(id, "reassign"), "{\"technicianId\": " + tech2 + "}").andExpect(status().isForbidden());
            postAs(name, url(id, "priority"), """
                    {"priority": "P3"}""").andExpect(status().isForbidden());
            getAs(name, url(id, "recommendations")).andExpect(status().isForbidden());
        }
        assertEquals("ASSIGNED", statusOf(id));
    }

    @Test
    void anAdminChangesThePriorityAndReassigns() throws Exception {
        long id = report();
        long tech2 = user("tech2").getId();

        String changed = body(postAs("admin", url(id, "priority"), """
                {"priority": "P3"}""").andExpect(status().isOk()));
        assertEquals("P3", field(changed, "$.priority"));

        String moved = body(postAs("admin", url(id, "reassign"), "{\"technicianId\": " + tech2 + "}").andExpect(status().isOk()));
        assertEquals("Sana Tech", field(moved, "$.assignee.name"));                    // tech2
        assertEquals(tech2, ((Number) JsonPath.read(moved, "$.assignee.id")).longValue());
    }

    @Test
    void anAdminMarksADuplicateAndClosingTooEarlyIs409() throws Exception {
        long original = report();
        long copy = report();

        postAs("admin", url(copy, "close")).andExpect(status().isConflict());              // not verified yet
        String marked = body(postAs("admin", url(copy, "duplicate"), "{\"originalId\": " + original + "}").andExpect(status().isOk()));

        assertEquals("DUPLICATE", field(marked, "$.status"));
        assertTrue(field(marked, "$.duplicateOfNumber").matches("INC\\d+"));
        postAs("admin", url(copy, "duplicate"), "{\"originalId\": 999999}").andExpect(status().isNotFound());
        postAs("admin", url(original, "duplicate"), "{\"originalId\": " + original + "}").andExpect(status().isBadRequest());
        postAs("admin", url(original, "duplicate"), "{}").andExpect(status().isBadRequest());
    }

    @Test
    void anAdminClosesAVerifiedIncident() throws Exception {
        long id = report();
        startWork(id);
        postAs("tech1", url(id, "resolve"), """
                {"resolution": "Restarted"}""").andExpect(status().isOk());
        postAs("emp1", url(id, "verify")).andExpect(status().isOk());

        assertEquals("CLOSED", field(body(postAs("admin", url(id, "close")).andExpect(status().isOk())), "$.status"));
    }

    @Test
    void anUnknownPriorityNameIs400AndTheRecommendationsHaveAtMostThreeTechnicians() throws Exception {
        long id = report();

        postAs("admin", url(id, "priority"), """
                {"priority": "P9"}""").andExpect(status().isBadRequest());
        postAs("admin", url(id, "priority"), "{}").andExpect(status().isBadRequest());

        String recommended = body(getAs("admin", url(id, "recommendations")).andExpect(status().isOk()));
        List<Object> technicians = JsonPath.read(recommended, "$");
        assertTrue(technicians.size() >= 1 && technicians.size() <= 3, "size " + technicians.size());
        assertNotNull(field(recommended, "$[0].fullName"));
    }

    // ---------- comments ----------

    @Test
    void notesAreForStaffAndTheReporterOnlySeesTheConversation() throws Exception {
        long id = report();
        startWork(id);

        String note = body(postAs("tech1", url(id, "comments"), """
                {"type": "NOTE", "body": "Gateway logs show timeouts"}""").andExpect(status().isCreated()));
        assertEquals("NOTE", field(note, "$.type"));
        postAs("admin", url(id, "comments"), """
                {"type": "NOTE", "body": "Vendor informed"}""").andExpect(status().isCreated());
        postAs("emp1", url(id, "comments"), """
                {"type": "NOTE", "body": "I am not staff"}""").andExpect(status().isForbidden());

        postAs("tech1", url(id, "comments"), """
                {"type": "INFO_REQUEST", "body": "Which users are affected?"}""").andExpect(status().isCreated());
        assertEquals("PENDING_INFO", statusOf(id));
        postAs("emp1", url(id, "comments"), """
                {"type": "INFO_RESPONSE", "body": "Everyone in Finance"}""").andExpect(status().isCreated());
        assertEquals("IN_PROGRESS", statusOf(id));

        List<String> forReporter = JsonPath.read(body(getAs("emp1", url(id, "comments")).andExpect(status().isOk())), "$[*].type");
        List<String> forStaff = JsonPath.read(body(getAs("tech1", url(id, "comments")).andExpect(status().isOk())), "$[*].type");
        assertEquals(List.of("INFO_REQUEST", "INFO_RESPONSE"), forReporter);
        assertEquals(List.of("NOTE", "NOTE", "INFO_REQUEST", "INFO_RESPONSE"), forStaff);
        getAs("emp2", url(id, "comments")).andExpect(status().isForbidden());
    }

    @Test
    void aBadCommentRequestIs400AndAnInfoRequestOutOfTurnIs409() throws Exception {
        long id = report();

        postAs("tech1", url(id, "comments"), """
                {"type": "INFO_REQUEST", "body": "Too early?"}""").andExpect(status().isConflict());   // still ASSIGNED
        postAs("tech1", url(id, "comments"), """
                {"body": "no type"}""").andExpect(status().isBadRequest());
        postAs("tech1", url(id, "comments"), """
                {"type": "WHATEVER", "body": "x"}""").andExpect(status().isBadRequest());
        postAs("tech1", url(id, "comments"), """
                {"type": "NOTE", "body": " "}""").andExpect(status().isBadRequest());
    }

    // ---------- category suggestion ----------

    @Test
    void theTextSuggestsTheMatchingCategoryAndUnrelatedTextSuggestsNothing() throws Exception {
        // the same texts the seed-data test of the suggester uses, split into a title and a description
        String payment = body(postAs("emp1", "/api/incidents/suggest-category", """
                {"title": "payment", "description": "transaction failing"}""").andExpect(status().isOk()));
        List<Object> suggestions = JsonPath.read(payment, "$");
        assertTrue(suggestions.size() >= 1 && suggestions.size() <= 3);
        assertEquals("Application", field(payment, "$[0].name"));
        Integer score = JsonPath.read(payment, "$[0].score");
        assertEquals(2, score.intValue());

        String login = body(postAs("emp1", "/api/incidents/suggest-category", """
                {"title": "cannot login", "description": "password locked"}""").andExpect(status().isOk()));
        assertEquals("Authentication", field(login, "$[0].name"));

        String nothing = body(postAs("emp1", "/api/incidents/suggest-category", """
                {"title": "lunch menu", "description": "for today"}""").andExpect(status().isOk()));
        List<Object> none = JsonPath.read(nothing, "$");
        assertTrue(none.isEmpty());

        postAs("emp1", "/api/incidents/suggest-category", "").andExpect(status().isBadRequest());
        postAs(null, "/api/incidents/suggest-category", "{}").andExpect(status().isUnauthorized());
    }
}
