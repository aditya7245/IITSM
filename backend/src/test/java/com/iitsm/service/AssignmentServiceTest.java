package com.iitsm.service;

import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentStatus;
import com.iitsm.domain.incident.Priority;
import com.iitsm.domain.user.Technician;
import com.iitsm.domain.user.User;
import com.iitsm.exception.InvalidInputException;
import com.iitsm.exception.TechnicianUnavailableException;
import com.iitsm.exception.WorkloadExceededException;
import com.iitsm.repository.*;
import com.iitsm.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Uses the seeded technicians:
 * lead.app (team lead, 8 years, max 5): Application Support 5, Authentication 4
 * tech1 (4 years, max 5): Application Support 5, Database 3
 * tech2 (2 years, max 5): Networking 4, Cloud 3
 * tech3 (6 years, max 3): Database 5, Application Support 2
 * Rolls back after each test.
 */
@Transactional
class AssignmentServiceTest extends AbstractIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-11-10T09:00:00Z");

    @Autowired AssignmentService assignment;
    @Autowired ConfigService config;
    @Autowired TechnicianRepository technicians;
    @Autowired IncidentRepository incidents;
    @Autowired UserRepository users;
    @Autowired ITServiceRepository services;
    @Autowired CategoryRepository categories;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    User reporter;
    Technician lead;
    Technician tech1;
    Technician tech2;
    Technician tech3;
    Incident applicationIncident;   // category Application, which needs the Application Support skill
    int serial;                     // keeps the incident numbers made by giveWorkload unique

    @BeforeEach
    void setUp() {
        reporter = users.findByEmailIgnoreCase("emp1@iitsm.local").orElseThrow();
        lead = technician("lead.app@iitsm.local");
        tech1 = technician("tech1@iitsm.local");
        tech2 = technician("tech2@iitsm.local");
        tech3 = technician("tech3@iitsm.local");
        applicationIncident = incidentIn("Application", "AS-MAIN");
    }

    private Technician technician(String email) {
        return (Technician) users.findByEmailIgnoreCase(email).orElseThrow();
    }

    private Incident incidentIn(String categoryName, String number) {
        return new Incident(number, "Title", "Description", reporter,
                services.findByName("Payment Application").orElseThrow(),
                categories.findByName(categoryName).orElseThrow(), (short) 2, (short) 2, 1, T0);
    }

    /** Gives a technician {@code count} active incidents (status ASSIGNED counts toward their workload). */
    private void giveWorkload(Technician tech, int count) {
        for (int i = 0; i < count; i++) {
            Incident incident = incidentIn("Application", "W-" + (++serial));
            incident.classify(Priority.P3, 8, T0.plusSeconds(60), T0.plusSeconds(600));
            incident.assignTo(tech);
            incidents.save(incident);
        }
        incidents.flush();
    }

    private List<String> emails(List<Technician> list) {
        return list.stream().map(Technician::getEmail).toList();
    }

    private String firstChoice() {
        return assignment.autoAssign(applicationIncident).orElseThrow().getEmail();
    }

    // ---------- skill based (the default) ----------

    @Test
    void skillBasedRanksByProficiencyThenExperienceAndDropsTechniciansWithoutTheSkill() {
        // Application Support: lead 5, tech1 5, tech3 2, tech2 none. lead and tech1 tie, so more experience (lead, 8 years) wins.
        assertEquals(List.of("lead.app@iitsm.local", "tech1@iitsm.local", "tech3@iitsm.local"),
                emails(assignment.recommend(applicationIncident, 10)));
        assertEquals("lead.app@iitsm.local", firstChoice());
    }

    @Test
    void recommendRespectsTheLimit() {
        assertEquals(List.of("lead.app@iitsm.local", "tech1@iitsm.local"),
                emails(assignment.recommend(applicationIncident, 2)));
        assertThrows(InvalidInputException.class, () -> assignment.recommend(applicationIncident, 0));
    }

    @Test
    void aTechnicianAtTheirWorkloadLimitIsSkippedButOneBelowItIsNot() {
        giveWorkload(lead, 4);                       // max is 5: still eligible, but busier than tech1
        assertEquals("tech1@iitsm.local", firstChoice());   // equal skill, fewer active incidents wins
        assertTrue(emails(assignment.recommend(applicationIncident, 10)).contains("lead.app@iitsm.local"));

        giveWorkload(tech1, 5);                      // tech1 is now full
        assertFalse(emails(assignment.recommend(applicationIncident, 10)).contains("tech1@iitsm.local"));
    }

    @Test
    void anUnavailableOrInactiveTechnicianIsSkipped() {
        lead.setAvailable(false);
        technicians.saveAndFlush(lead);
        assertEquals("tech1@iitsm.local", firstChoice());

        tech1.deactivate();
        technicians.saveAndFlush(tech1);
        assertEquals("tech3@iitsm.local", firstChoice());
    }

    @Test
    void nobodyEligibleGivesAnEmptyResult() {
        jdbc.update("update users set available = false where role in ('TECHNICIAN', 'TEAM_LEAD')");
        em.clear();
        assertTrue(assignment.autoAssign(applicationIncident).isEmpty());
        assertTrue(assignment.recommend(applicationIncident, 3).isEmpty());
    }

    @Test
    void whenNobodyHasTheRequiredSkillSkillBasedGivesNothing() {
        Incident storage = incidentIn("Storage", "AS-STORAGE");   // needs the Storage skill, which nobody has
        assertTrue(assignment.autoAssign(storage).isEmpty());
        assertTrue(assignment.recommend(storage, 3).isEmpty());
    }

    // ---------- other strategies, chosen from the settings ----------

    @Test
    void loadBasedIgnoresSkillsAndPicksTheLeastBusyThenTheMostExperienced() {
        config.set(ConfigKeys.ASSIGNMENT_STRATEGY, "LOAD_BASED");
        assertEquals("lead.app@iitsm.local", firstChoice());      // nobody busy: most experienced (8 years)

        giveWorkload(lead, 1);
        assertEquals("tech3@iitsm.local", firstChoice());         // now tech3 (6 years)

        // load-based ignores skills, so the networking specialist is a candidate too
        assertTrue(emails(assignment.recommend(applicationIncident, 10)).contains("tech2@iitsm.local"));
    }

    @Test
    void loadBasedStillWorksForTheCategoryNobodyHasTheSkillFor() {
        config.set(ConfigKeys.ASSIGNMENT_STRATEGY, "LOAD_BASED");
        assertTrue(assignment.autoAssign(incidentIn("Storage", "AS-STORAGE")).isPresent());
    }

    @Test
    void roundRobinTakesTurnsThroughEveryEligibleTechnician() {
        config.set(ConfigKeys.ASSIGNMENT_STRATEGY, "ROUND_ROBIN");

        List<String> picks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            picks.add(firstChoice());
        }
        // four eligible technicians: the first four picks are all different and the next four repeat them in the same order
        assertEquals(4, picks.subList(0, 4).stream().distinct().count());
        assertEquals(picks.subList(0, 4), picks.subList(4, 8));
    }

    @Test
    void recommendingNeverAdvancesTheRoundRobin() {
        config.set(ConfigKeys.ASSIGNMENT_STRATEGY, "ROUND_ROBIN");
        List<String> first = emails(assignment.recommend(applicationIncident, 4));
        List<String> second = emails(assignment.recommend(applicationIncident, 4));
        assertEquals(first, second);
    }

    @Test
    void anUnknownStrategyNameFallsBackToSkillBased() {
        config.set(ConfigKeys.ASSIGNMENT_STRATEGY, "NOT_A_STRATEGY");
        assertEquals("lead.app@iitsm.local", firstChoice());
        config.set(ConfigKeys.ASSIGNMENT_STRATEGY, "  load_based ");   // names are trimmed and case-insensitive
        assertEquals("lead.app@iitsm.local", firstChoice());
    }

    // ---------- the incident is never changed ----------

    @Test
    void choosingAndRecommendingNeverChangeTheIncident() {
        assignment.autoAssign(applicationIncident);
        assignment.recommend(applicationIncident, 3);
        assertNull(applicationIncident.getAssignee());
        assertEquals(IncidentStatus.NEW, applicationIncident.getStatus());
    }

    // ---------- manual assignment ----------

    @Test
    void anAdminMayPickAnyAvailableTechnicianEvenWithoutTheSkill() {
        assertEquals("tech2@iitsm.local", assignment.assignManually(applicationIncident, tech2.getId()).getEmail());
        assertNull(applicationIncident.getAssignee());   // the caller makes the change, not this method
    }

    @Test
    void aTechnicianWhoIsUnavailableInactiveOrNotATechnicianIsRefused() {
        tech2.setAvailable(false);
        technicians.saveAndFlush(tech2);
        assertThrows(TechnicianUnavailableException.class, () -> assignment.assignManually(applicationIncident, tech2.getId()));

        tech1.deactivate();
        technicians.saveAndFlush(tech1);
        assertThrows(TechnicianUnavailableException.class, () -> assignment.assignManually(applicationIncident, tech1.getId()));

        Long employeeId = users.findByEmailIgnoreCase("emp2@iitsm.local").orElseThrow().getId();
        assertThrows(TechnicianUnavailableException.class, () -> assignment.assignManually(applicationIncident, employeeId));
        assertThrows(TechnicianUnavailableException.class, () -> assignment.assignManually(applicationIncident, 999_999L));
        assertThrows(TechnicianUnavailableException.class, () -> assignment.assignManually(applicationIncident, null));
    }

    @Test
    void aTechnicianAtTheirLimitIsRefusedButOneBelowItIsAccepted() {
        giveWorkload(tech3, 2);                          // max is 3
        assertEquals("tech3@iitsm.local", assignment.assignManually(applicationIncident, tech3.getId()).getEmail());

        giveWorkload(tech3, 1);                          // now 3 of 3
        WorkloadExceededException e = assertThrows(WorkloadExceededException.class,
                () -> assignment.assignManually(applicationIncident, tech3.getId()));
        assertTrue(e.getMessage().contains("3"));
    }

    @Test
    void resolvedIncidentsDoNotCountTowardsTheLimit() {
        giveWorkload(tech3, 3);                          // full
        assertThrows(WorkloadExceededException.class, () -> assignment.assignManually(applicationIncident, tech3.getId()));

        jdbc.update("update incidents set status = 'RESOLVED' where assignee_id = ?", tech3.getId());
        em.clear();
        assertEquals("tech3@iitsm.local", assignment.assignManually(applicationIncident, tech3.getId()).getEmail());
    }
}
