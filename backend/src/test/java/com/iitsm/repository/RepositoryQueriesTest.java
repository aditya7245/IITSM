package com.iitsm.repository;

import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentStatus;
import com.iitsm.domain.catalog.ITService;
import com.iitsm.domain.user.*;
import com.iitsm.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Custom repository queries from item 1.10. Rolls back after each test. */
@Transactional
class RepositoryQueriesTest extends AbstractIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-11-10T09:00:00Z");

    @Autowired UserRepository users;
    @Autowired TechnicianRepository technicians;
    @Autowired IncidentRepository incidents;
    @Autowired ITServiceRepository services;
    @Autowired CategoryRepository categories;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;

    // ---------- users by exact type ----------

    private Set<String> emails(List<? extends User> list) {
        return list.stream().map(User::getEmail).collect(Collectors.toSet());
    }

    @Test
    void exactTypeAdministratorExcludesTheItManager() {
        assertEquals(Set.of("admin@iitsm.local"), emails(users.findAllByExactType(Administrator.class)));
    }

    @Test
    void exactTypeItManagerReturnsOnlyTheManager() {
        assertEquals(Set.of("manager@iitsm.local"), emails(users.findAllByExactType(ITManager.class)));
    }

    @Test
    void exactTypeTechnicianExcludesTheTeamLead() {
        assertEquals(Set.of("tech1@iitsm.local", "tech2@iitsm.local", "tech3@iitsm.local"),
                emails(users.findAllByExactType(Technician.class)));
        assertEquals(Set.of("lead.app@iitsm.local"), emails(users.findAllByExactType(TeamLead.class)));
    }

    @Test
    void exactTypeEmployeeReturnsBothEmployees() {
        assertEquals(Set.of("emp1@iitsm.local", "emp2@iitsm.local"), emails(users.findAllByExactType(Employee.class)));
    }

    // ---------- available technicians ----------

    @Test
    void availableTechniciansIncludeTeamLeadAndSkipUnavailableOrInactive() {
        assertEquals(Set.of("tech1@iitsm.local", "tech2@iitsm.local", "tech3@iitsm.local", "lead.app@iitsm.local"),
                emails(technicians.findByActiveTrueAndAvailableTrue()));

        Technician tech2 = (Technician) users.findByEmailIgnoreCase("tech2@iitsm.local").orElseThrow();
        Technician tech3 = (Technician) users.findByEmailIgnoreCase("tech3@iitsm.local").orElseThrow();
        tech2.setAvailable(false);
        tech3.deactivate();
        technicians.saveAll(List.of(tech2, tech3));

        assertEquals(Set.of("tech1@iitsm.local", "lead.app@iitsm.local"),
                emails(technicians.findByActiveTrueAndAvailableTrue()));
    }

    // ---------- incident queries ----------

    private Incident newIncident(String number, ITService service, Instant createdAt) {
        return incidents.saveAndFlush(new Incident(number, "Title " + number, "Description " + number,
                users.findByEmailIgnoreCase("emp1@iitsm.local").orElseThrow(), service,
                categories.findByName("Application").orElseThrow(), (short) 2, (short) 2, 1, createdAt));
    }

    /** No behaviour methods exist yet (item 2.4), so set status and assignee with SQL. */
    private void setStatusAndAssignee(Incident incident, IncidentStatus status, Long assigneeId) {
        jdbc.update("update incidents set status = ?, assignee_id = ? where id = ?",
                status.name(), assigneeId, incident.getId());
    }

    @Test
    void workloadCountOnlyCountsWorkloadStatusesForThatTechnician() {
        ITService service = services.findByName("Payment Application").orElseThrow();
        Long tech1 = users.findByEmailIgnoreCase("tech1@iitsm.local").orElseThrow().getId();
        Long tech2 = users.findByEmailIgnoreCase("tech2@iitsm.local").orElseThrow().getId();
        long before = incidents.countByAssigneeIdAndStatusIn(tech1, IncidentStatus.workloadStatuses());

        setStatusAndAssignee(newIncident("Q-1", service, T0), IncidentStatus.ASSIGNED, tech1);
        setStatusAndAssignee(newIncident("Q-2", service, T0), IncidentStatus.IN_PROGRESS, tech1);
        setStatusAndAssignee(newIncident("Q-3", service, T0), IncidentStatus.RESOLVED, tech1);
        setStatusAndAssignee(newIncident("Q-4", service, T0), IncidentStatus.CLOSED, tech1);
        setStatusAndAssignee(newIncident("Q-5", service, T0), IncidentStatus.ASSIGNED, tech2);
        em.clear();

        assertEquals(before + 2, incidents.countByAssigneeIdAndStatusIn(tech1, IncidentStatus.workloadStatuses()));
    }

    @Test
    void findByStatusNotInExcludesTerminalIncidents() {
        ITService service = services.findByName("Payment Application").orElseThrow();
        Incident open = newIncident("Q-6", service, T0);
        Incident closed = newIncident("Q-7", service, T0);
        setStatusAndAssignee(closed, IncidentStatus.CLOSED, null);
        em.clear();

        Set<String> numbers = incidents.findByStatusNotIn(IncidentStatus.terminalStatuses()).stream()
                .map(Incident::getIncidentNumber).collect(Collectors.toSet());
        assertTrue(numbers.contains("Q-6"));
        assertFalse(numbers.contains("Q-7"));
    }

    @Test
    void duplicateCandidateQueryFiltersByServiceStatusAndAge() {
        ITService payment = services.findByName("Payment Application").orElseThrow();
        ITService vpn = services.findByName("VPN Gateway").orElseThrow();
        newIncident("Q-8", payment, T0);                              // match
        newIncident("Q-9", payment, T0.minusSeconds(7200));           // too old
        newIncident("Q-10", vpn, T0);                                 // other service
        setStatusAndAssignee(newIncident("Q-11", payment, T0), IncidentStatus.CANCELLED, null); // terminal
        em.clear();

        Set<String> numbers = incidents.findByServiceIdAndStatusNotInAndCreatedAtAfter(
                        payment.getId(), IncidentStatus.terminalStatuses(), T0.minusSeconds(3600)).stream()
                .map(Incident::getIncidentNumber).collect(Collectors.toSet());
        assertEquals(Set.of("Q-8"), numbers);
    }
}
