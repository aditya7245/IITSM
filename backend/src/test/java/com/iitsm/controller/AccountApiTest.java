package com.iitsm.controller;

import com.iitsm.support.AbstractApiTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Signing up and "who am I" through the real security chain. */
class AccountApiTest extends AbstractApiTest {

    private String registerJson(String email, String password, String fullName, String extra) {
        return "{\"email\": \"%s\", \"password\": \"%s\", \"fullName\": \"%s\"%s}".formatted(email, password, fullName, extra);
    }

    private String register(String email) throws Exception {
        return body(postAs(null, "/api/auth/register", registerJson(email, "Password@123", "New Person", ", \"department\": \"Legal\""))
                .andExpect(status().isCreated()));
    }

    // ---------- signing up ----------

    @Test
    void anyoneCanRegisterWithoutLoggingInAndGetsAnEmployeeAccount() throws Exception {
        String body = register("new.person@iitsm.local");

        assertEquals("new.person@iitsm.local", text(body, "$.email"));
        assertEquals("New Person", text(body, "$.fullName"));
        assertEquals("EMPLOYEE", text(body, "$.role"));
        assertEquals("Legal", text(body, "$.department"));
        assertEquals(true, com.jayway.jsonpath.JsonPath.<Boolean>read(body, "$.active"));
        assertTrue(number(body, "$.id") > 0);
    }

    @Test
    void theResponseNeverContainsAPassword() throws Exception {
        String body = register("new.person@iitsm.local");

        assertFalse(body.toLowerCase().contains("password"), body);
        assertFalse(body.contains("Password@123"));
        assertFalse(body.contains("$2a$"));
    }

    @Test
    void theNewAccountCanLogInAndUseItsTokenStraightAway() throws Exception {
        register("new.person@iitsm.local");

        String login = body(loginWith("new.person@iitsm.local", "Password@123").andExpect(status().isOk()));
        assertEquals("EMPLOYEE", text(login, "$.role"));
        String token = text(login, "$.token");
        String me = body(send(null, get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk()));

        assertEquals("new.person@iitsm.local", text(me, "$.email"));
        loginWith("new.person@iitsm.local", "wrong-password").andExpect(status().isUnauthorized());
    }

    @Test
    void theEmailIsLookedUpIgnoringCapitalLetters() throws Exception {
        register("mixed.case@iitsm.local");

        loginWith("MIXED.Case@iitsm.local", "Password@123").andExpect(status().isOk());
    }

    @Test
    void aRoleInTheRequestIsIgnoredSoNobodyCanSignUpAsAnAdministrator() throws Exception {
        String body = body(postAs(null, "/api/auth/register",
                registerJson("sneaky@iitsm.local", "Password@123", "Sneaky Person", ", \"role\": \"ADMINISTRATOR\", \"active\": false"))
                .andExpect(status().isCreated()));

        assertEquals("EMPLOYEE", text(body, "$.role"));
        assertEquals("EMPLOYEE", text(body(loginWith("sneaky@iitsm.local", "Password@123").andExpect(status().isOk())), "$.role"));
        String token = text(body(loginWith("sneaky@iitsm.local", "Password@123").andExpect(status().isOk())), "$.token");
        send(null, get("/api/admin/users").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
    }

    @Test
    void anEmailThatIsAlreadyUsedIsA409EvenWithDifferentCapitals() throws Exception {
        register("taken@iitsm.local");

        postAs(null, "/api/auth/register", registerJson("taken@iitsm.local", "Password@123", "Other", "")).andExpect(status().isConflict());
        postAs(null, "/api/auth/register", registerJson("TAKEN@iitsm.local", "Password@123", "Other", "")).andExpect(status().isConflict());
        postAs(null, "/api/auth/register", registerJson("emp1@iitsm.local", "Password@123", "Other", "")).andExpect(status().isConflict());
        postAs(null, "/api/auth/register", registerJson("ADMIN@iitsm.local", "Password@123", "Other", "")).andExpect(status().isConflict());
    }

    @Test
    void theRulesForThePasswordTheEmailAndTheNameAreEnforced() throws Exception {
        String shortPassword = body(postAs(null, "/api/auth/register", registerJson("a@iitsm.local", "1234567", "Name", ""))
                .andExpect(status().isBadRequest()));
        assertNotNull(text(shortPassword, "$.fieldErrors.password"));
        postAs(null, "/api/auth/register", registerJson("a@iitsm.local", "x".repeat(73), "Name", "")).andExpect(status().isBadRequest());
        postAs(null, "/api/auth/register", registerJson("a@iitsm.local", "12345678", "Name", "")).andExpect(status().isCreated());

        String badEmail = body(postAs(null, "/api/auth/register", registerJson("not-an-email", "Password@123", "Name", ""))
                .andExpect(status().isBadRequest()));
        assertNotNull(text(badEmail, "$.fieldErrors.email"));
        String blankName = body(postAs(null, "/api/auth/register", registerJson("b@iitsm.local", "Password@123", " ", ""))
                .andExpect(status().isBadRequest()));
        assertNotNull(text(blankName, "$.fieldErrors.fullName"));
        postAs(null, "/api/auth/register", registerJson("c@iitsm.local", "Password@123", "Name", ", \"department\": \"" + "d".repeat(101) + "\""))
                .andExpect(status().isBadRequest());
        postAs(null, "/api/auth/register", "{\"password\": \"Password@123\", \"fullName\": \"Name\"}").andExpect(status().isBadRequest());
        postAs(null, "/api/auth/register", "{ not json").andExpect(status().isBadRequest());
    }

    // ---------- who am I ----------

    @Test
    void meShowsAnEmployeesDepartment() throws Exception {
        String body = body(getAs("emp1", "/api/auth/me").andExpect(status().isOk()));

        assertEquals("emp1@iitsm.local", text(body, "$.email"));
        assertEquals("Asha Employee", text(body, "$.fullName"));
        assertEquals("EMPLOYEE", text(body, "$.role"));
        assertEquals("Finance", text(body, "$.department"));
        assertNull(com.jayway.jsonpath.JsonPath.read(body, "$.team"));
        assertNull(com.jayway.jsonpath.JsonPath.read(body, "$.maxWorkload"));
    }

    @Test
    void meShowsATechniciansTeamLimitAndAvailability() throws Exception {
        String body = body(getAs("tech1", "/api/auth/me").andExpect(status().isOk()));

        assertEquals("TECHNICIAN", text(body, "$.role"));
        assertEquals("Application Support", text(body, "$.team.name"));
        assertEquals(5, number(body, "$.maxWorkload"));
        assertEquals(4, number(body, "$.experienceYears"));
        assertEquals(true, com.jayway.jsonpath.JsonPath.<Boolean>read(body, "$.available"));
        assertNull(com.jayway.jsonpath.JsonPath.read(body, "$.department"));
    }

    @Test
    void meShowsTheTeamLeadAdministratorAndITManagerRoles() throws Exception {
        assertEquals("TEAM_LEAD", text(body(getAs("lead.app", "/api/auth/me").andExpect(status().isOk())), "$.role"));
        assertEquals("ADMINISTRATOR", text(body(getAs("admin", "/api/auth/me").andExpect(status().isOk())), "$.role"));
        assertEquals("IT_MANAGER", text(body(getAs("manager", "/api/auth/me").andExpect(status().isOk())), "$.role"));
        String lead = body(getAs("lead.app", "/api/auth/me").andExpect(status().isOk()));
        assertEquals("Application Support", text(lead, "$.team.name"));
        assertEquals(8, number(lead, "$.experienceYears"));
        assertNull(com.jayway.jsonpath.JsonPath.read(body(getAs("admin", "/api/auth/me").andExpect(status().isOk())), "$.team"));
    }

    @Test
    void meNeedsALogin() throws Exception {
        getAs(null, "/api/auth/me").andExpect(status().isUnauthorized());
    }

    // ---------- reading the lists ----------

    @Test
    void anyLoggedInUserCanReadTheSkillsInNameOrder() throws Exception {
        List<String> names = list(body(getAs("emp1", "/api/skills").andExpect(status().isOk())), "$[*].name");

        assertEquals(List.of("Application Support", "Authentication", "Cloud", "Database", "Hardware", "Networking",
                "Security", "Software", "Storage"), names);
        getAs(null, "/api/skills").andExpect(status().isUnauthorized());
    }
}
