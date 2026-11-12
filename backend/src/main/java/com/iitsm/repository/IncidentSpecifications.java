package com.iitsm.repository;

import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentStatus;
import com.iitsm.domain.incident.Priority;
import com.iitsm.domain.incident.SlaStatus;
import com.iitsm.domain.user.Technician;
import com.iitsm.domain.user.Team;
import com.iitsm.domain.user.User;
import com.iitsm.util.Days;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.Locale;

/**
 * Small building blocks for searching incidents. Each one is a condition that can be combined with the others
 * using {@code and}. {@link #visibleTo(User)} is the one that decides what a person is allowed to see.
 */
public final class IncidentSpecifications {

    private IncidentSpecifications() { }

    // ------------------------------------------------------------------ who may see what

    /**
     * Only the incidents this user may see in a list: an employee sees those they reported, a technician those
     * assigned to them, a team lead also those assigned to technicians of the team they lead, and administrators
     * and IT managers see everything.
     */
    public static Specification<Incident> visibleTo(User user) {
        Long userId = user.getId();
        return switch (user.getRole()) {
            case ADMINISTRATOR, IT_MANAGER -> (root, query, cb) -> cb.conjunction();
            case EMPLOYEE -> reportedBy(userId);
            case TECHNICIAN -> assignedTo(userId);
            case TEAM_LEAD -> assignedTo(userId).or(assignedToTeamLedBy(userId));
        };
    }

    public static Specification<Incident> reportedBy(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("reporter").get("id"), userId);
    }

    public static Specification<Incident> assignedTo(Long technicianId) {
        return (root, query, cb) -> cb.equal(root.get("assignee").get("id"), technicianId);
    }

    /** Assigned to a technician who belongs to a team led by the given team lead. */
    public static Specification<Incident> assignedToTeamLedBy(Long leadId) {
        return (root, query, cb) -> {
            Join<Incident, Technician> assignee = root.join("assignee", JoinType.LEFT);
            Join<Technician, Team> team = assignee.join("team", JoinType.LEFT);
            return cb.equal(team.get("lead").get("id"), leadId);
        };
    }

    // ------------------------------------------------------------------ filters

    /** The incident number contains the text, ignoring capital letters. The characters % and _ mean themselves. */
    public static Specification<Incident> numberContains(String text) {
        String pattern = likePattern(text);
        return (root, query, cb) -> cb.like(cb.lower(root.get("incidentNumber")), pattern, '\\');
    }

    public static Specification<Incident> hasStatus(IncidentStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Incident> hasPriority(Priority priority) {
        return (root, query, cb) -> cb.equal(root.get("priority"), priority);
    }

    public static Specification<Incident> hasSlaStatus(SlaStatus slaStatus) {
        return (root, query, cb) -> cb.equal(root.get("slaStatus"), slaStatus);
    }

    public static Specification<Incident> inCategory(Long categoryId) {
        return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Incident> onService(Long serviceId) {
        return (root, query, cb) -> cb.equal(root.get("service").get("id"), serviceId);
    }

    /** Created on or after the start of this day. */
    public static Specification<Incident> createdFrom(LocalDate day) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), Days.startOf(day));
    }

    /** Created on or before this day, so up to the start of the next one. */
    public static Specification<Incident> createdTo(LocalDate day) {
        return (root, query, cb) -> cb.lessThan(root.get("createdAt"), Days.startOfNextDay(day));
    }

    /** "%text%" in lower case, with the characters that mean something to LIKE made literal (escape character is a backslash). */
    static String likePattern(String text) {
        String escaped = text.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
