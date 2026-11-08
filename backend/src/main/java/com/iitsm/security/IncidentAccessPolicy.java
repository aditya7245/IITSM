package com.iitsm.security;

import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.user.Role;
import com.iitsm.domain.user.Team;
import com.iitsm.domain.user.Technician;
import com.iitsm.domain.user.TeamLead;
import com.iitsm.domain.user.User;
import com.iitsm.exception.UnauthorizedOperationException;
import org.springframework.stereotype.Component;

/**
 * The one place that decides who may do what with an incident. Role checks on the web endpoints say what a
 * kind of user may attempt; this class adds the "is it YOUR incident" rules. Every {@code require...} method
 * throws {@link UnauthorizedOperationException} (HTTP 403) when the answer is no.
 * <pre>
 * view incident or its history      reporter, assignee, team lead of the assignee's team, any admin
 * report an incident                any active user
 * acknowledge, start, request info, resolve     the assignee
 * add an internal note              the assignee or an admin
 * verify, reopen, answer info request, give feedback    the reporter
 * cancel                            the reporter or an admin
 * reassign, change priority, mark duplicate, close without feedback    an admin
 * </pre>
 * "Admin" means an administrator or an IT manager.
 */
@Component
public class IncidentAccessPolicy {

    // ------------------------------------------------------------ report and view

    public boolean canReport(User user) {
        return user != null && user.isActive();
    }

    public boolean canView(User user, Incident incident) {
        return isReporter(user, incident)
                || isAssignee(user, incident)
                || isTeamLeadOfAssignee(user, incident)
                || isAdmin(user);
    }

    public void requireView(User user, Incident incident) {
        require(canView(user, incident));
    }

    // ------------------------------------------------------------ technician actions

    /** Acknowledge, start, request info, resolve. */
    public void requireAssignee(User user, Incident incident) {
        require(isAssignee(user, incident));
    }

    /** Adding an internal note. */
    public void requireAssigneeOrAdmin(User user, Incident incident) {
        require(isAssignee(user, incident) || isAdmin(user));
    }

    // ------------------------------------------------------------ reporter actions

    /** Verify, reopen, answer an information request, give feedback. */
    public void requireReporter(User user, Incident incident) {
        require(isReporter(user, incident));
    }

    /** Cancelling. */
    public void requireReporterOrAdmin(User user, Incident incident) {
        require(isReporter(user, incident) || isAdmin(user));
    }

    // ------------------------------------------------------------ admin actions

    /** Reassign, change priority, mark duplicate, close without feedback. */
    public void requireAdmin(User user) {
        require(isAdmin(user));
    }

    // ------------------------------------------------------------ helpers

    private static void require(boolean allowed) {
        if (!allowed) {
            throw new UnauthorizedOperationException();
        }
    }

    private static boolean isAdmin(User user) {
        if (user == null) {
            return false;
        }
        Role role = user.getRole();   // asks the object itself, so it also works on lazily loaded proxies
        return role == Role.ADMINISTRATOR || role == Role.IT_MANAGER;
    }

    private static boolean isReporter(User user, Incident incident) {
        return sameUser(user, incident.getReporter());
    }

    private static boolean isAssignee(User user, Incident incident) {
        return sameUser(user, incident.getAssignee());
    }

    private static boolean isTeamLeadOfAssignee(User user, Incident incident) {
        Technician assignee = incident.getAssignee();
        Team team = assignee == null ? null : assignee.getTeam();
        TeamLead lead = team == null ? null : team.getLead();
        return sameUser(user, lead);
    }

    private static boolean sameUser(User a, User b) {
        return a != null && b != null && a.getId() != null && a.getId().equals(b.getId());
    }
}
