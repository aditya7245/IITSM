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
 * User, team, technician and skill administration through the real security chain. The seeded data: eight users
 * (admin "System Admin", manager "IT Manager", emp1 "Asha Employee", emp2 "Ravi Employee", lead.app "Meera Lead",
 * tech1 "Kiran Tech", tech2 "Sana Tech", tech3 "Dev Tech"); teams Application Support (lead Meera Lead; members
 * Meera and Kiran) and Infrastructure (no lead; members Sana and Dev); skills as listed in the reference data.
 * Seeded skills: Kiran has Application Support 5 and Database 3, Sana has Networking 4 and Cloud 3, Dev has
 * Database 5 and Application Support 2.
 */
class AdminUserApiTest extends AbstractApiTest {

    // ---------- helpers ----------

    private String createJson(String email, String role, String extra) {
        return "{\"email\": \"%s\", \"password\": \"Password@123\", \"fullName\": \"New %s\", \"role\": \"%s\"%s}"
                .formatted(email, role, role, extra);
    }

    private long create(String email, String role, String extra) throws Exception {
        return number(body(postAs("admin", "/api/admin/users", createJson(email, role, extra)).andExpect(status().isCreated())), "$.id");
    }

    private long teamId(String name) throws Exception {
        List<Map<String, Object>> teams = list(body(getAs("admin", "/api/admin/teams").andExpect(status().isOk())), "$");
        return ((Number) teams.stream().filter(t -> name.equals(t.get("name"))).findFirst().orElseThrow().get("id")).longValue();
    }

    private long skillId(String name) throws Exception {
        List<Map<String, Object>> all = list(body(getAs("admin", "/api/skills").andExpect(status().isOk())), "$");
        return ((Number) all.stream().filter(s -> name.equals(s.get("name"))).findFirst().orElseThrow().get("id")).longValue();
    }

    private String assigneeOfNewPaymentIncident() throws Exception {
        long id = submitIncident("emp1", "Payment application outage", "Users cannot pay",
                "Application", "Payment Application", 3, 3, 250);
        return text(body(getAs("admin", "/api/incidents/" + id).andExpect(status().isOk())), "$.assignee.name");
    }

    private List<String> names(String query) throws Exception {
        return list(body(getAs("admin", "/api/admin/users" + query).andExpect(status().isOk())), "$.content[*].fullName");
    }

    private ResultActions call(String user, String method, String url, String json) throws Exception {
        return switch (method) {
            case "GET" -> getAs(user, url);
            case "POST" -> postAs(user, url, json);
            case "PUT" -> putAs(user, url, json);
            default -> throw new IllegalArgumentException(method);
        };
    }

    // ---------- creating users ----------

    @Test
    void anAdministratorCreatesAUserOfEveryRoleAndEachCanLogIn() throws Exception {
        long teamId = teamId("Infrastructure");
        for (String role : List.of("EMPLOYEE", "TECHNICIAN", "TEAM_LEAD", "ADMINISTRATOR", "IT_MANAGER")) {
            String email = "new." + role.toLowerCase() + "@iitsm.local";
            boolean technician = role.equals("TECHNICIAN") || role.equals("TEAM_LEAD");

            String created = body(postAs("admin", "/api/admin/users",
                    createJson(email, role, technician ? ", \"teamId\": " + teamId : "")).andExpect(status().isCreated()));

            assertEquals(role, text(created, "$.role"));
            assertEquals(email, text(created, "$.email"));
            assertFalse(created.toLowerCase().contains("password"));
            assertEquals(role, text(body(loginWith(email, "Password@123").andExpect(status().isOk())), "$.role"), "login as " + role);
        }
    }

    @Test
    void aTechnicianGetsTheTeamAndNumbersGivenOrTheDefaults() throws Exception {
        long teamId = teamId("Infrastructure");

        String given = body(postAs("admin", "/api/admin/users",
                createJson("tech.a@iitsm.local", "TECHNICIAN", ", \"teamId\": " + teamId + ", \"maxWorkload\": 3, \"experienceYears\": 2"))
                .andExpect(status().isCreated()));
        assertEquals("Infrastructure", text(given, "$.team.name"));
        assertEquals(3, number(given, "$.maxWorkload"));
        assertEquals(2, number(given, "$.experienceYears"));
        assertEquals(true, JsonPath.<Boolean>read(given, "$.available"));

        String defaults = body(postAs("admin", "/api/admin/users", createJson("tech.b@iitsm.local", "TECHNICIAN", ""))
                .andExpect(status().isCreated()));
        assertEquals(5, number(defaults, "$.maxWorkload"));
        assertEquals(0, number(defaults, "$.experienceYears"));
        assertNull(JsonPath.read(defaults, "$.team"));
    }

    @Test
    void anEmployeeGetsTheDepartment() throws Exception {
        String created = body(postAs("admin", "/api/admin/users",
                createJson("emp.new@iitsm.local", "EMPLOYEE", ", \"department\": \"Legal\"")).andExpect(status().isCreated()));

        assertEquals("Legal", text(created, "$.department"));
    }

    @Test
    void anEmailThatIsAlreadyUsedIsA409() throws Exception {
        postAs("admin", "/api/admin/users", createJson("emp1@iitsm.local", "EMPLOYEE", "")).andExpect(status().isConflict());
        postAs("admin", "/api/admin/users", createJson("EMP1@IITSM.LOCAL", "EMPLOYEE", "")).andExpect(status().isConflict());
        create("fresh@iitsm.local", "EMPLOYEE", "");
        postAs("admin", "/api/admin/users", createJson("Fresh@iitsm.local", "TECHNICIAN", "")).andExpect(status().isConflict());
    }

    @Test
    void invalidNewUsersAreRejected() throws Exception {
        long teamId = teamId("Infrastructure");

        postAs("admin", "/api/admin/users", "{\"email\": \"a@iitsm.local\", \"password\": \"Password@123\", \"fullName\": \"A\"}")
                .andExpect(status().isBadRequest());                                                             // no role
        postAs("admin", "/api/admin/users", createJson("a@iitsm.local", "BOSS", "")).andExpect(status().isBadRequest());   // unknown role
        postAs("admin", "/api/admin/users", "{\"email\": \"a@iitsm.local\", \"password\": \"short\", \"fullName\": \"A\", \"role\": \"EMPLOYEE\"}")
                .andExpect(status().isBadRequest());
        postAs("admin", "/api/admin/users", createJson("not-an-email", "EMPLOYEE", "")).andExpect(status().isBadRequest());
        postAs("admin", "/api/admin/users", createJson("a@iitsm.local", "TECHNICIAN", ", \"maxWorkload\": 0")).andExpect(status().isBadRequest());
        postAs("admin", "/api/admin/users", createJson("a@iitsm.local", "TECHNICIAN", ", \"experienceYears\": -1")).andExpect(status().isBadRequest());
        postAs("admin", "/api/admin/users", createJson("a@iitsm.local", "EMPLOYEE", ", \"teamId\": " + teamId)).andExpect(status().isBadRequest());
        postAs("admin", "/api/admin/users", createJson("a@iitsm.local", "TECHNICIAN", ", \"teamId\": 999999")).andExpect(status().isNotFound());
        assertEquals(8, number(body(getAs("admin", "/api/admin/users").andExpect(status().isOk())), "$.totalElements"));   // nothing was created
    }

    // ---------- listing and reading ----------

    @Test
    void allEightSeededUsersAreListedInNameOrder() throws Exception {
        assertEquals(List.of("Asha Employee", "Dev Tech", "IT Manager", "Kiran Tech", "Meera Lead", "Ravi Employee",
                "Sana Tech", "System Admin"), names(""));
    }

    @Test
    void theRoleFilterMeansExactlyThatRole() throws Exception {
        assertEquals(List.of("Asha Employee", "Ravi Employee"), names("?role=EMPLOYEE"));
        assertEquals(List.of("Dev Tech", "Kiran Tech", "Sana Tech"), names("?role=TECHNICIAN"));    // the team lead is not a plain technician
        assertEquals(List.of("Meera Lead"), names("?role=TEAM_LEAD"));
        assertEquals(List.of("System Admin"), names("?role=ADMINISTRATOR"));                          // the IT manager is not listed here
        assertEquals(List.of("IT Manager"), names("?role=IT_MANAGER"));
    }

    @Test
    void thePagesHaveTheRightContentAndTotals() throws Exception {
        String first = body(getAs("admin", "/api/admin/users?size=3&page=0").andExpect(status().isOk()));
        assertEquals(List.of("Asha Employee", "Dev Tech", "IT Manager"), list(first, "$.content[*].fullName"));
        assertEquals(8, number(first, "$.totalElements"));
        assertEquals(3, number(first, "$.totalPages"));

        assertEquals(List.of("Sana Tech", "System Admin"), names("?size=3&page=2"));
        assertEquals(List.of(), names("?size=3&page=3"));
        assertEquals(List.of("Dev Tech"), names("?role=TECHNICIAN&size=1&page=0"));
        assertEquals(100, number(body(getAs("admin", "/api/admin/users?size=500").andExpect(status().isOk())), "$.size"));
        assertEquals(20, number(body(getAs("admin", "/api/admin/users").andExpect(status().isOk())), "$.size"));
    }

    @Test
    void aSortInTheRequestChangesNothingAndFailsNothing() throws Exception {
        List<String> alphabetical = names("");

        assertEquals(alphabetical, names("?sort=email,desc"));
        assertEquals(alphabetical, names("?sort=nonsense"));
    }

    @Test
    void anUnknownRoleFilterIsA400() throws Exception {
        getAs("admin", "/api/admin/users?role=BOSS").andExpect(status().isBadRequest());
    }

    @Test
    void oneUserCanBeReadWithTheFieldsOfItsKind() throws Exception {
        String tech = body(getAs("admin", "/api/admin/users/" + user("tech3").getId()).andExpect(status().isOk()));

        assertEquals("tech3@iitsm.local", text(tech, "$.email"));
        assertEquals("Dev Tech", text(tech, "$.fullName"));
        assertEquals("TECHNICIAN", text(tech, "$.role"));
        assertEquals("Infrastructure", text(tech, "$.team.name"));
        assertEquals(3, number(tech, "$.maxWorkload"));
        assertEquals(6, number(tech, "$.experienceYears"));

        String employee = body(getAs("admin", "/api/admin/users/" + user("emp2").getId()).andExpect(status().isOk()));
        assertEquals("Sales", text(employee, "$.department"));
        assertFalse(employee.toLowerCase().contains("password"));
        getAs("admin", "/api/admin/users/999999").andExpect(status().isNotFound());
        getAs("admin", "/api/admin/users/abc").andExpect(status().isBadRequest());
    }

    // ---------- changing a user ----------

    @Test
    void anAdministratorRenamesAnEmployeeAndChangesTheDepartment() throws Exception {
        long id = user("emp2").getId();

        String updated = body(putAs("admin", "/api/admin/users/" + id,
                "{\"fullName\": \"Ravi Kumar\", \"active\": true, \"department\": \"Support\"}").andExpect(status().isOk()));

        assertEquals("Ravi Kumar", text(updated, "$.fullName"));
        assertEquals("Support", text(updated, "$.department"));
        assertEquals("Ravi Kumar", text(body(getAs("admin", "/api/admin/users/" + id).andExpect(status().isOk())), "$.fullName"));
    }

    @Test
    void aDepartmentGivenForSomeoneWhoIsNotAnEmployeeIsIgnored() throws Exception {
        String updated = body(putAs("admin", "/api/admin/users/" + user("tech1").getId(),
                "{\"fullName\": \"Kiran Tech\", \"active\": true, \"department\": \"Sales\"}").andExpect(status().isOk()));

        assertNull(JsonPath.read(updated, "$.department"));
        assertEquals("TECHNICIAN", text(updated, "$.role"));
    }

    @Test
    void aDeactivatedAccountCannotLogInUntilItIsActivatedAgain() throws Exception {
        long id = user("emp2").getId();
        loginWith("emp2@iitsm.local", "Password@123").andExpect(status().isOk());

        String off = body(putAs("admin", "/api/admin/users/" + id, "{\"fullName\": \"Ravi Employee\", \"active\": false}").andExpect(status().isOk()));
        assertEquals(false, JsonPath.<Boolean>read(off, "$.active"));
        loginWith("emp2@iitsm.local", "Password@123").andExpect(status().isUnauthorized());

        putAs("admin", "/api/admin/users/" + id, "{\"fullName\": \"Ravi Employee\", \"active\": true}").andExpect(status().isOk());
        loginWith("emp2@iitsm.local", "Password@123").andExpect(status().isOk());
    }

    @Test
    void anAdministratorCannotDeactivateTheirOwnAccount() throws Exception {
        long adminId = user("admin").getId();

        putAs("admin", "/api/admin/users/" + adminId, "{\"fullName\": \"System Admin\", \"active\": false}").andExpect(status().isBadRequest());
        putAs("admin", "/api/admin/users/" + adminId, "{\"fullName\": \"Chief Admin\", \"active\": true}").andExpect(status().isOk());

        assertEquals("ADMINISTRATOR", text(body(loginWith("admin@iitsm.local", "Password@123").andExpect(status().isOk())), "$.role"));
    }

    @Test
    void invalidChangesAreRejected() throws Exception {
        long id = user("emp2").getId();

        putAs("admin", "/api/admin/users/" + id, "{\"fullName\": \" \", \"active\": true}").andExpect(status().isBadRequest());
        putAs("admin", "/api/admin/users/" + id, "{\"fullName\": \"Ravi\"}").andExpect(status().isBadRequest());     // no active
        putAs("admin", "/api/admin/users/" + id, "{\"fullName\": \"" + "n".repeat(121) + "\", \"active\": true}").andExpect(status().isBadRequest());
        putAs("admin", "/api/admin/users/999999", "{\"fullName\": \"Ravi\", \"active\": true}").andExpect(status().isNotFound());
        assertEquals("Ravi Employee", text(body(getAs("admin", "/api/admin/users/" + id).andExpect(status().isOk())), "$.fullName"));
    }

    @Test
    void aNewPasswordReplacesTheOldOne() throws Exception {
        long id = user("emp2").getId();

        String response = body(putAs("admin", "/api/admin/users/" + id + "/password", "{\"password\": \"NewPassw0rd!\"}")
                .andExpect(status().isNoContent()));

        assertEquals("", response);
        loginWith("emp2@iitsm.local", "Password@123").andExpect(status().isUnauthorized());
        loginWith("emp2@iitsm.local", "NewPassw0rd!").andExpect(status().isOk());
    }

    @Test
    void aBadNewPasswordOrAnUnknownUserIsRefusedAndTheOldPasswordStillWorks() throws Exception {
        long id = user("emp2").getId();

        putAs("admin", "/api/admin/users/" + id + "/password", "{\"password\": \"1234567\"}").andExpect(status().isBadRequest());
        putAs("admin", "/api/admin/users/" + id + "/password", "{\"password\": \"" + "x".repeat(73) + "\"}").andExpect(status().isBadRequest());
        putAs("admin", "/api/admin/users/" + id + "/password", "{}").andExpect(status().isBadRequest());
        putAs("admin", "/api/admin/users/999999/password", "{\"password\": \"NewPassw0rd!\"}").andExpect(status().isNotFound());

        loginWith("emp2@iitsm.local", "Password@123").andExpect(status().isOk());
    }

    // ---------- teams ----------

    @Test
    void theSeededTeamsAndTheirLeadsAreListed() throws Exception {
        List<Map<String, Object>> teams = list(body(getAs("admin", "/api/admin/teams").andExpect(status().isOk())), "$");

        assertEquals(List.of("Application Support", "Infrastructure"), teams.stream().map(t -> t.get("name")).toList());
        @SuppressWarnings("unchecked") Map<String, Object> lead = (Map<String, Object>) teams.get(0).get("lead");
        assertEquals("Meera Lead", lead.get("name"));
        assertNull(teams.get(1).get("lead"));
    }

    @Test
    void aTeamCanBeCreatedButNotTwiceWithTheSameName() throws Exception {
        String created = body(postAs("admin", "/api/admin/teams", "{\"name\": \"Security\"}").andExpect(status().isCreated()));

        assertEquals("Security", text(created, "$.name"));
        assertNull(JsonPath.read(created, "$.lead"));
        postAs("admin", "/api/admin/teams", "{\"name\": \"Security\"}").andExpect(status().isConflict());
        postAs("admin", "/api/admin/teams", "{\"name\": \"security\"}").andExpect(status().isConflict());
        postAs("admin", "/api/admin/teams", "{\"name\": \"  \"}").andExpect(status().isBadRequest());
        postAs("admin", "/api/admin/teams", "{}").andExpect(status().isBadRequest());
        List<String> names = list(body(getAs("admin", "/api/admin/teams").andExpect(status().isOk())), "$[*].name");
        assertEquals(List.of("Application Support", "Infrastructure", "Security"), names);
    }

    @Test
    void aTeamLeadWhoBelongsToTheTeamCanBeMadeItsLead() throws Exception {
        long securityId = number(body(postAs("admin", "/api/admin/teams", "{\"name\": \"Security\"}").andExpect(status().isCreated())), "$.id");
        long leadId = create("sec.lead@iitsm.local", "TEAM_LEAD", ", \"teamId\": " + securityId);

        String team = body(putAs("admin", "/api/admin/teams/" + securityId + "/lead", "{\"leadId\": " + leadId + "}").andExpect(status().isOk()));

        assertEquals("Security", text(team, "$.name"));
        assertEquals(leadId, number(team, "$.lead.id"));
        assertEquals("New TEAM_LEAD", text(team, "$.lead.name"));
    }

    @Test
    void theLeadMustBeAnActiveTeamLeadWhoIsAMemberOfTheTeam() throws Exception {
        long securityId = number(body(postAs("admin", "/api/admin/teams", "{\"name\": \"Security\"}").andExpect(status().isCreated())), "$.id");
        String url = "/api/admin/teams/" + securityId + "/lead";

        putAs("admin", url, "{\"leadId\": " + user("tech1").getId() + "}").andExpect(status().isBadRequest());          // not a team lead
        putAs("admin", url, "{\"leadId\": " + user("emp1").getId() + "}").andExpect(status().isBadRequest());
        putAs("admin", url, "{\"leadId\": " + user("lead.app").getId() + "}").andExpect(status().isBadRequest());        // member of another team
        long homeless = create("no.team@iitsm.local", "TEAM_LEAD", "");
        putAs("admin", url, "{\"leadId\": " + homeless + "}").andExpect(status().isBadRequest());                        // member of no team
        long inactive = create("off.lead@iitsm.local", "TEAM_LEAD", ", \"teamId\": " + securityId);
        putAs("admin", "/api/admin/users/" + inactive, "{\"fullName\": \"Off Lead\", \"active\": false}").andExpect(status().isOk());
        putAs("admin", url, "{\"leadId\": " + inactive + "}").andExpect(status().isBadRequest());                        // switched off

        putAs("admin", url, "{\"leadId\": 999999}").andExpect(status().isNotFound());
        putAs("admin", "/api/admin/teams/999999/lead", "{\"leadId\": " + user("lead.app").getId() + "}").andExpect(status().isNotFound());
        putAs("admin", url, "{}").andExpect(status().isBadRequest());
    }

    @Test
    void theSeededLeadStaysLeadAfterAFailedChange() throws Exception {
        long appSupport = teamId("Application Support");

        putAs("admin", "/api/admin/teams/" + appSupport + "/lead", "{\"leadId\": " + user("tech1").getId() + "}").andExpect(status().isBadRequest());

        List<Map<String, Object>> teams = list(body(getAs("admin", "/api/admin/teams").andExpect(status().isOk())), "$");
        @SuppressWarnings("unchecked") Map<String, Object> lead = (Map<String, Object>) teams.get(0).get("lead");
        assertEquals("Meera Lead", lead.get("name"));
    }

    // ---------- technicians ----------

    private String technicianJson(int maxWorkload, boolean available, int experience, Long teamId) {
        return "{\"maxWorkload\": %d, \"available\": %s, \"experienceYears\": %d, \"teamId\": %s}"
                .formatted(maxWorkload, available, experience, teamId);
    }

    @Test
    void aTechniciansLimitsAvailabilityExperienceAndTeamCanBeChanged() throws Exception {
        long tech3 = user("tech3").getId();   // Dev Tech: Infrastructure, limit 3, 6 years

        String updated = body(putAs("admin", "/api/admin/technicians/" + tech3,
                technicianJson(4, false, 7, teamId("Application Support"))).andExpect(status().isOk()));

        assertEquals("Application Support", text(updated, "$.team.name"));
        assertEquals(4, number(updated, "$.maxWorkload"));
        assertEquals(false, JsonPath.<Boolean>read(updated, "$.available"));
        assertEquals(7, number(updated, "$.experienceYears"));
        String read = body(getAs("admin", "/api/admin/users/" + tech3).andExpect(status().isOk()));
        assertEquals("Application Support", text(read, "$.team.name"));
        assertEquals(4, number(read, "$.maxWorkload"));
    }

    @Test
    void aTechnicianCanBeTakenOutOfTheirTeam() throws Exception {
        String updated = body(putAs("admin", "/api/admin/technicians/" + user("tech2").getId(),
                technicianJson(5, true, 2, null)).andExpect(status().isOk()));

        assertNull(JsonPath.read(updated, "$.team"));
    }

    @Test
    void aTeamLeadCannotBeMovedOutOfTheTeamTheyLeadButTheirNumbersCanChange() throws Exception {
        long lead = user("lead.app").getId();

        putAs("admin", "/api/admin/technicians/" + lead, technicianJson(5, true, 8, teamId("Infrastructure"))).andExpect(status().isBadRequest());
        putAs("admin", "/api/admin/technicians/" + lead, technicianJson(5, true, 8, null)).andExpect(status().isBadRequest());
        String same = body(putAs("admin", "/api/admin/technicians/" + lead, technicianJson(6, true, 9, teamId("Application Support")))
                .andExpect(status().isOk()));

        assertEquals("TEAM_LEAD", text(same, "$.role"));
        assertEquals(6, number(same, "$.maxWorkload"));
        assertEquals("Application Support", text(same, "$.team.name"));
    }

    @Test
    void invalidTechnicianChangesAreRejected() throws Exception {
        long tech1 = user("tech1").getId();

        putAs("admin", "/api/admin/technicians/" + tech1, technicianJson(0, true, 4, null)).andExpect(status().isBadRequest());
        putAs("admin", "/api/admin/technicians/" + tech1, technicianJson(5, true, -1, null)).andExpect(status().isBadRequest());
        putAs("admin", "/api/admin/technicians/" + tech1, "{\"maxWorkload\": 5, \"experienceYears\": 4}").andExpect(status().isBadRequest());
        putAs("admin", "/api/admin/technicians/" + tech1, "{\"available\": true, \"experienceYears\": 4}").andExpect(status().isBadRequest());
        putAs("admin", "/api/admin/technicians/" + tech1, technicianJson(5, true, 4, 999999L)).andExpect(status().isNotFound());
        putAs("admin", "/api/admin/technicians/" + user("emp1").getId(), technicianJson(5, true, 4, null)).andExpect(status().isNotFound());
        putAs("admin", "/api/admin/technicians/999999", technicianJson(5, true, 4, null)).andExpect(status().isNotFound());
        assertEquals(5, number(body(getAs("admin", "/api/admin/users/" + tech1).andExpect(status().isOk())), "$.maxWorkload"));
    }

    @Test
    void loweringATechniciansLimitSendsTheNextIncidentToSomeoneElse() throws Exception {
        putAs("admin", "/api/admin/technicians/" + user("tech1").getId(), technicianJson(1, true, 4, teamId("Application Support")))
                .andExpect(status().isOk());

        assertEquals("Kiran Tech", assigneeOfNewPaymentIncident());     // takes the first one: now at the limit of 1
        assertEquals("Dev Tech", assigneeOfNewPaymentIncident());       // the next best with the skill
    }

    // ---------- technician skills ----------

    private String skillsJson(String... skillAndProficiency) {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < skillAndProficiency.length; i += 2) {
            if (i > 0) {
                items.append(", ");
            }
            items.append("{\"skillId\": %s, \"proficiency\": %s}".formatted(skillAndProficiency[i], skillAndProficiency[i + 1]));
        }
        return "{\"skills\": [" + items + "]}";
    }

    @Test
    void theSeededSkillsOfATechnicianAreReadInNameOrder() throws Exception {
        String body = body(getAs("admin", "/api/admin/technicians/" + user("tech2").getId() + "/skills").andExpect(status().isOk()));

        assertEquals(List.of("Cloud", "Networking"), list(body, "$[*].skillName"));
        assertEquals(List.of(3, 4), list(body, "$[*].proficiency"));
        assertEquals(skillId("Cloud"), number(body, "$[0].skillId"));
    }

    @Test
    void replacingTheSkillsLeavesExactlyTheNewSet() throws Exception {
        long tech2 = user("tech2").getId();

        String replaced = body(putAs("admin", "/api/admin/technicians/" + tech2 + "/skills",
                skillsJson("" + skillId("Database"), "2", "" + skillId("Authentication"), "5")).andExpect(status().isOk()));

        assertEquals(List.of("Authentication", "Database"), list(replaced, "$[*].skillName"));
        assertEquals(List.of(5, 2), list(replaced, "$[*].proficiency"));
        String read = body(getAs("admin", "/api/admin/technicians/" + tech2 + "/skills").andExpect(status().isOk()));
        assertEquals(List.of("Authentication", "Database"), list(read, "$[*].skillName"));      // Cloud and Networking are gone
    }

    @Test
    void aSkillThatStaysCanChangeItsProficiency() throws Exception {
        long tech2 = user("tech2").getId();

        String replaced = body(putAs("admin", "/api/admin/technicians/" + tech2 + "/skills",
                skillsJson("" + skillId("Networking"), "1")).andExpect(status().isOk()));

        assertEquals(List.of("Networking"), list(replaced, "$[*].skillName"));
        assertEquals(List.of(1), list(replaced, "$[*].proficiency"));
    }

    @Test
    void anEmptyListRemovesAllSkills() throws Exception {
        long tech2 = user("tech2").getId();

        assertEquals(List.of(), list(body(putAs("admin", "/api/admin/technicians/" + tech2 + "/skills", skillsJson())
                .andExpect(status().isOk())), "$"));
        assertEquals(List.of(), list(body(getAs("admin", "/api/admin/technicians/" + tech2 + "/skills").andExpect(status().isOk())), "$"));
    }

    @Test
    void invalidSkillListsAreRejectedAndTheOldSkillsStay() throws Exception {
        long tech2 = user("tech2").getId();
        String url = "/api/admin/technicians/" + tech2 + "/skills";
        String database = "" + skillId("Database");

        putAs("admin", url, skillsJson(database, "2", database, "3")).andExpect(status().isBadRequest());     // twice
        putAs("admin", url, skillsJson("999999", "3")).andExpect(status().isNotFound());                       // unknown skill
        putAs("admin", url, skillsJson(database, "0")).andExpect(status().isBadRequest());
        putAs("admin", url, skillsJson(database, "6")).andExpect(status().isBadRequest());
        putAs("admin", url, "{}").andExpect(status().isBadRequest());
        putAs("admin", url, "{\"skills\": null}").andExpect(status().isBadRequest());
        putAs("admin", url, "{\"skills\": [{\"proficiency\": 3}]}").andExpect(status().isBadRequest());

        assertEquals(List.of("Cloud", "Networking"),
                list(body(getAs("admin", url).andExpect(status().isOk())), "$[*].skillName"));
    }

    @Test
    void skillsBelongOnlyToTechnicians() throws Exception {
        long employee = user("emp1").getId();

        getAs("admin", "/api/admin/technicians/" + employee + "/skills").andExpect(status().isNotFound());
        putAs("admin", "/api/admin/technicians/" + employee + "/skills", skillsJson()).andExpect(status().isNotFound());
        getAs("admin", "/api/admin/technicians/999999/skills").andExpect(status().isNotFound());
    }

    @Test
    void changingTheSkillsChangesWhoGetsTheNextIncident() throws Exception {
        assertEquals("Kiran Tech", assigneeOfNewPaymentIncident());                      // Application Support 5

        putAs("admin", "/api/admin/technicians/" + user("tech1").getId() + "/skills",
                skillsJson("" + skillId("Application Support"), "1")).andExpect(status().isOk());

        assertEquals("Dev Tech", assigneeOfNewPaymentIncident());                        // Application Support 2 now ranks first
    }

    // ---------- a technician's own availability ----------

    @Test
    void aTechnicianCanMarkThemselvesUnavailableAndTheNextIncidentSkipsThem() throws Exception {
        String off = body(putAs("tech1", "/api/technicians/me/availability", "{\"available\": false}").andExpect(status().isOk()));
        assertEquals(false, JsonPath.<Boolean>read(off, "$.available"));
        assertEquals("TECHNICIAN", text(off, "$.role"));
        assertEquals(false, JsonPath.<Boolean>read(body(getAs("tech1", "/api/auth/me").andExpect(status().isOk())), "$.available"));

        assertEquals("Dev Tech", assigneeOfNewPaymentIncident());

        putAs("tech1", "/api/technicians/me/availability", "{\"available\": true}").andExpect(status().isOk());
        assertEquals("Kiran Tech", assigneeOfNewPaymentIncident());
    }

    @Test
    void aTeamLeadCanChangeTheirAvailabilityToo() throws Exception {
        String off = body(putAs("lead.app", "/api/technicians/me/availability", "{\"available\": false}").andExpect(status().isOk()));

        assertEquals("TEAM_LEAD", text(off, "$.role"));
        assertEquals(false, JsonPath.<Boolean>read(off, "$.available"));
    }

    @Test
    void onlyTechniciansHaveAnAvailability() throws Exception {
        putAs("emp1", "/api/technicians/me/availability", "{\"available\": false}").andExpect(status().isForbidden());
        putAs("admin", "/api/technicians/me/availability", "{\"available\": false}").andExpect(status().isForbidden());
        putAs("manager", "/api/technicians/me/availability", "{\"available\": false}").andExpect(status().isForbidden());
        putAs(null, "/api/technicians/me/availability", "{\"available\": false}").andExpect(status().isUnauthorized());
        putAs("tech1", "/api/technicians/me/availability", "{}").andExpect(status().isBadRequest());
        putAs("tech1", "/api/technicians/me/availability", "{\"available\": \"maybe\"}").andExpect(status().isBadRequest());
    }

    // ---------- administrators only ----------

    @Test
    void everyAdministrationAddressRefusesEveryoneElseAndChangesNothing() throws Exception {
        long tech1 = user("tech1").getId();
        long emp1 = user("emp1").getId();
        List<String[]> requests = List.of(
                new String[]{"GET", "/api/admin/users", ""},
                new String[]{"GET", "/api/admin/users/" + emp1, ""},
                new String[]{"POST", "/api/admin/users", createJson("intruder@iitsm.local", "ADMINISTRATOR", "")},
                new String[]{"PUT", "/api/admin/users/" + emp1, "{\"fullName\": \"Hacked\", \"active\": false}"},
                new String[]{"PUT", "/api/admin/users/" + emp1 + "/password", "{\"password\": \"Hacked1234\"}"},
                new String[]{"GET", "/api/admin/teams", ""},
                new String[]{"POST", "/api/admin/teams", "{\"name\": \"Intruders\"}"},
                new String[]{"PUT", "/api/admin/teams/" + teamId("Infrastructure") + "/lead", "{\"leadId\": " + user("lead.app").getId() + "}"},
                new String[]{"PUT", "/api/admin/technicians/" + tech1, technicianJson(1, false, 0, null)},
                new String[]{"GET", "/api/admin/technicians/" + tech1 + "/skills", ""},
                new String[]{"PUT", "/api/admin/technicians/" + tech1 + "/skills", skillsJson()});

        for (String user : List.of("emp1", "tech1", "lead.app")) {
            for (String[] request : requests) {
                call(user, request[0], request[1], request[2]).andExpect(status().isForbidden());
            }
        }
        for (String[] request : requests) {
            call(null, request[0], request[1], request[2]).andExpect(status().isUnauthorized());
        }
        // an IT manager counts as an administrator
        call("manager", "GET", "/api/admin/users", "").andExpect(status().isOk());

        assertEquals(8, number(body(getAs("admin", "/api/admin/users").andExpect(status().isOk())), "$.totalElements"));
        assertEquals("Asha Employee", text(body(getAs("admin", "/api/admin/users/" + emp1).andExpect(status().isOk())), "$.fullName"));
        loginWith("emp1@iitsm.local", "Password@123").andExpect(status().isOk());
        assertEquals(5, number(body(getAs("admin", "/api/admin/users/" + tech1).andExpect(status().isOk())), "$.maxWorkload"));
    }
}
