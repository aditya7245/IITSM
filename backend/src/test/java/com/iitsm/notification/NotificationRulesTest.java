package com.iitsm.notification;

import com.iitsm.domain.user.*;
import com.iitsm.notification.NotificationRules.Delivery;
import com.iitsm.observer.IncidentEventType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.iitsm.observer.IncidentEventType.*;
import static org.junit.jupiter.api.Assertions.*;

/** The notification table from the checklist, row by row. */
class NotificationRulesTest {

    private final Employee reporter = new Employee("rita@x", "h", "Reporter", "Finance");
    private final Team team = new Team("App Support");
    private final TeamLead lead = new TeamLead("lena@x", "h", "Lead", team, 5, 8);
    private final Technician assignee = new Technician("tom@x", "h", "Tech", team, 5, 3);
    private final Administrator admin = new Administrator("alex@x", "h", "Admin");
    private final ITManager manager = new ITManager("mia@x", "h", "Manager");
    private final Employee outsider = new Employee("xavi@x", "h", "Outsider", "Sales");

    NotificationRulesTest() {
        team.assignLead(lead);
    }

    /** Describes who gets notified as names, with "+email" for those who also get an email. */
    private List<String> plan(IncidentEventType type, Technician assigneeOrNull, List<User> explicit) {
        List<Delivery> deliveries = NotificationRules.plan(NotificationRules.ruleFor(type), reporter, assigneeOrNull,
                List.of(admin, manager), explicit);
        return deliveries.stream().map(d -> d.user().getFullName() + (d.email() ? "+email" : "")).toList();
    }

    private List<String> plan(IncidentEventType type) {
        return plan(type, assignee, List.of());
    }

    @Test
    void everyEventTypeHasARule() {
        for (IncidentEventType type : IncidentEventType.values()) {
            assertNotNull(NotificationRules.ruleFor(type), type.name());
        }
    }

    @Test
    void eventsWithNobodyToNotify() {
        assertEquals(List.of(), plan(CLASSIFIED));
        assertEquals(List.of(), plan(INVESTIGATION_STARTED));
        assertEquals(List.of(), plan(ESCALATED));   // escalation notifies only the explicit recipients
    }

    @Test
    void reporterOnlyEvents() {
        assertEquals(List.of("Reporter+email"), plan(CREATED));
        assertEquals(List.of("Reporter"), plan(ACKNOWLEDGED));
        assertEquals(List.of("Reporter+email"), plan(INFO_REQUESTED));
        assertEquals(List.of("Reporter+email"), plan(RESOLVED));
        assertEquals(List.of("Reporter"), plan(CLOSED));
    }

    @Test
    void assigneeOnlyEvents() {
        assertEquals(List.of("Tech"), plan(INFO_RECEIVED));
        assertEquals(List.of("Tech"), plan(REOPENED));
        assertEquals(List.of("Tech"), plan(VERIFIED));
    }

    @Test
    void reporterAndAssigneeEvents() {
        assertEquals(List.of("Reporter", "Tech"), plan(PRIORITY_CHANGED));
        assertEquals(List.of("Reporter", "Tech"), plan(CANCELLED));
        assertEquals(List.of("Reporter", "Tech"), plan(MARKED_DUPLICATE));
    }

    @Test
    void assignedEmailsOnlyTheAssignee() {
        assertEquals(List.of("Tech+email", "Reporter"), plan(ASSIGNED));
    }

    @Test
    void slaWarningGoesToTheAssigneeAndTheirTeamLead() {
        assertEquals(List.of("Tech", "Lead"), plan(SLA_WARNING));
    }

    @Test
    void slaViolationGoesToTheAssigneeAndAllAdminsWithEmail() {
        assertEquals(List.of("Tech+email", "Admin+email", "Manager+email"), plan(SLA_VIOLATED));
    }

    @Test
    void failedAssignmentGoesToAllAdminsWithEmail() {
        assertEquals(List.of("Admin+email", "Manager+email"), plan(ASSIGNMENT_FAILED));
    }

    @Test
    void explicitRecipientsReplaceTheNormalAudience() {
        assertEquals(List.of("Lead+email", "Admin+email"), plan(ESCALATED, assignee, List.of(lead, admin)));
        // even for an event that normally goes to the reporter
        assertEquals(List.of("Outsider+email"), plan(CREATED, assignee, List.of(outsider)));
        assertEquals(List.of("Outsider"), plan(ACKNOWLEDGED, assignee, List.of(outsider)));
    }

    @Test
    void withoutAnAssigneeTheAssigneeAndTeamLeadAudiencesAreSkipped() {
        assertEquals(List.of("Reporter"), plan(ASSIGNED, null, List.of()));
        assertEquals(List.of("Reporter"), plan(CANCELLED, null, List.of()));
        assertEquals(List.of(), plan(SLA_WARNING, null, List.of()));
        assertEquals(List.of("Admin+email", "Manager+email"), plan(SLA_VIOLATED, null, List.of()));
    }

    @Test
    void anAssigneeWithoutATeamHasNoTeamLeadToNotify() {
        Technician loner = new Technician("lone@x", "h", "Loner", null, 5, 1);
        assertEquals(List.of("Loner"), plan(SLA_WARNING, loner, List.of()));
    }

    @Test
    void aTeamWithoutALeadHasNoTeamLeadToNotify() {
        Technician other = new Technician("o@x", "h", "Other", new Team("No lead"), 5, 1);
        assertEquals(List.of("Other"), plan(SLA_WARNING, other, List.of()));
    }

    @Test
    void aPersonWhoIsInTwoAudiencesIsNotifiedOnceWithAnEmailIfAnyReasonWantsOne() {
        // the assignee is also the team lead of their own team
        Team soloTeam = new Team("Solo");
        TeamLead soloLead = new TeamLead("s@x", "h", "Solo", soloTeam, 5, 5);
        soloTeam.assignLead(soloLead);
        assertEquals(List.of("Solo"), plan(SLA_WARNING, soloLead, List.of()));
        assertEquals(List.of("Solo+email", "Admin+email", "Manager+email"), plan(SLA_VIOLATED, soloLead, List.of()));
    }

    @Test
    void whenTheReporterIsAlsoTheAssigneeTheyGetOneNotification() {
        Technician both = new Technician("both@x", "h", "Both", team, 5, 3);
        List<Delivery> deliveries = NotificationRules.plan(NotificationRules.ruleFor(ASSIGNED), both, both, List.of(), List.of());
        assertEquals(1, deliveries.size());
        assertTrue(deliveries.get(0).email());   // as assignee they get the email
    }
}
