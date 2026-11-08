package com.iitsm.security;

import com.iitsm.domain.common.BaseEntity;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.Priority;
import com.iitsm.domain.user.*;
import com.iitsm.exception.UnauthorizedOperationException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Every cell of the access table: for each action, who is allowed and who is refused. */
class IncidentAccessPolicyTest {

    private static final Instant T0 = Instant.parse("2026-11-10T09:00:00Z");

    private final IncidentAccessPolicy policy = new IncidentAccessPolicy();

    // ---- the cast ----
    private final Employee reporter = user(new Employee("rita@x", "h", "Reporter", "Finance"), 1);
    private final Employee otherEmployee = user(new Employee("eve@x", "h", "Other Employee", "Sales"), 2);
    private final Team team = new Team("App Support");
    private final TeamLead lead = user(new TeamLead("lena@x", "h", "Lead", team, 5, 8), 3);
    private final Technician assignee = user(new Technician("tom@x", "h", "Assignee", team, 5, 3), 4);
    private final Technician otherTech = user(new Technician("tess@x", "h", "Other Tech", new Team("Infra"), 5, 2), 5);
    private final TeamLead otherLead = user(new TeamLead("olga@x", "h", "Other Lead", new Team("Infra 2"), 5, 8), 6);
    private final Administrator admin = user(new Administrator("alex@x", "h", "Admin"), 7);
    private final ITManager manager = user(new ITManager("mia@x", "h", "Manager"), 8);

    private final Incident incident = assignedIncident();

    private static <U extends User> U user(U user, long id) {
        setId(user, id);
        return user;
    }

    private static void setId(Object entity, long id) {
        try {
            Field f = BaseEntity.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private Incident assignedIncident() {
        team.assignLead(lead);
        Incident i = new Incident("INC1", "t", "d", reporter, null, null, (short) 2, (short) 2, 1, T0);
        i.classify(Priority.P2, 12, T0.plusSeconds(900), T0.plusSeconds(7200));
        i.assignTo(assignee);
        return i;
    }

    private Incident unassignedIncident() {
        return new Incident("INC2", "t", "d", reporter, null, null, (short) 2, (short) 2, 1, T0);
    }

    private interface Check {
        void run(User user);
    }

    private static void assertAllowed(Check check, User... users) {
        for (User u : users) {
            assertDoesNotThrow(() -> check.run(u), u.getFullName() + " should be allowed");
        }
    }

    private static void assertRefused(Check check, User... users) {
        for (User u : users) {
            assertThrows(UnauthorizedOperationException.class, () -> check.run(u), u.getFullName() + " should be refused");
        }
    }

    // ---- report ----

    @Test
    void anyActiveUserCanReportAnIncident() {
        for (User u : List.of(reporter, assignee, lead, admin, manager)) {
            assertTrue(policy.canReport(u), u.getFullName());
        }
    }

    @Test
    void anInactiveOrMissingUserCannotReport() {
        Employee gone = user(new Employee("gone@x", "h", "Gone", "Sales"), 9);
        gone.deactivate();
        assertFalse(policy.canReport(gone));
        assertFalse(policy.canReport(null));
    }

    // ---- view ----

    @Test
    void reporterAssigneeTheirTeamLeadAndAdminsMayView() {
        assertAllowed(u -> policy.requireView(u, incident), reporter, assignee, lead, admin, manager);
    }

    @Test
    void everyoneElseMayNotView() {
        assertRefused(u -> policy.requireView(u, incident), otherEmployee, otherTech, otherLead);
    }

    @Test
    void anUnassignedIncidentCanOnlyBeSeenByTheReporterAndAdmins() {
        Incident unassigned = unassignedIncident();
        assertAllowed(u -> policy.requireView(u, unassigned), reporter, admin, manager);
        assertRefused(u -> policy.requireView(u, unassigned), assignee, lead, otherEmployee, otherTech);
    }

    // ---- technician actions: acknowledge, start, request info, resolve ----

    @Test
    void onlyTheAssigneeMayWorkOnTheIncident() {
        assertAllowed(u -> policy.requireAssignee(u, incident), assignee);
        assertRefused(u -> policy.requireAssignee(u, incident), reporter, lead, admin, manager, otherTech, otherEmployee);
    }

    @Test
    void nobodyMayWorkOnAnIncidentThatHasNoAssignee() {
        Incident unassigned = unassignedIncident();
        assertRefused(u -> policy.requireAssignee(u, unassigned), reporter, assignee, lead, admin, manager);
    }

    // ---- internal notes ----

    @Test
    void theAssigneeAndAdminsMayAddNotes() {
        assertAllowed(u -> policy.requireAssigneeOrAdmin(u, incident), assignee, admin, manager);
        assertRefused(u -> policy.requireAssigneeOrAdmin(u, incident), reporter, lead, otherTech, otherEmployee);
    }

    // ---- reporter actions: verify, reopen, answer info, feedback ----

    @Test
    void onlyTheReporterMayVerifyReopenAnswerOrGiveFeedback() {
        assertAllowed(u -> policy.requireReporter(u, incident), reporter);
        assertRefused(u -> policy.requireReporter(u, incident), assignee, lead, admin, manager, otherEmployee, otherTech);
    }

    // ---- cancel ----

    @Test
    void theReporterAndAdminsMayCancel() {
        assertAllowed(u -> policy.requireReporterOrAdmin(u, incident), reporter, admin, manager);
        assertRefused(u -> policy.requireReporterOrAdmin(u, incident), assignee, lead, otherEmployee, otherTech);
    }

    // ---- admin actions: reassign, change priority, mark duplicate, close ----

    @Test
    void onlyAdminsMayReassignChangePriorityMarkDuplicateOrClose() {
        assertAllowed(policy::requireAdmin, admin, manager);
        assertRefused(policy::requireAdmin, reporter, assignee, lead, otherEmployee, otherTech, otherLead);
    }

    @Test
    void aMissingUserIsRefusedEverywhere() {
        assertThrows(UnauthorizedOperationException.class, () -> policy.requireView(null, incident));
        assertThrows(UnauthorizedOperationException.class, () -> policy.requireAssignee(null, incident));
        assertThrows(UnauthorizedOperationException.class, () -> policy.requireReporter(null, incident));
        assertThrows(UnauthorizedOperationException.class, () -> policy.requireAdmin(null));
    }

    @Test
    void theRefusalMapsToHttp403() {
        UnauthorizedOperationException e = assertThrows(UnauthorizedOperationException.class, () -> policy.requireAdmin(reporter));
        assertEquals(403, e.getStatus().value());
    }
}
