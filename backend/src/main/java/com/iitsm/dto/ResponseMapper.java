package com.iitsm.dto;

import com.iitsm.domain.audit.AuditLog;
import com.iitsm.domain.catalog.Category;
import com.iitsm.domain.catalog.ITService;
import com.iitsm.domain.catalog.Skill;
import com.iitsm.domain.catalog.TechnicianSkill;
import com.iitsm.domain.incident.Feedback;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentComment;
import com.iitsm.domain.notification.Notification;
import com.iitsm.domain.sla.SlaPolicy;
import com.iitsm.domain.user.Employee;
import com.iitsm.domain.user.Team;
import com.iitsm.domain.user.Technician;
import com.iitsm.domain.user.User;
import com.iitsm.service.CategoryScore;
import com.iitsm.service.CreateIncidentResult;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;

/**
 * Turns entities into the response objects the API sends, so database objects never leave the service layer.
 * <p>
 * Many entities point to others lazily (an incident to its reporter, for example), so this must be called while the
 * transaction that loaded them is still open. Anything that does not exist yet (no assignee, no priority) becomes null.
 */
@Component
public class ResponseMapper {

    public IncidentSummaryResponse toSummary(Incident incident) {
        return new IncidentSummaryResponse(incident.getId(), incident.getIncidentNumber(), incident.getTitle(),
                incident.getPriority(), incident.getStatus(), incident.getSlaStatus(), ref(incident.getReporter()),
                ref(incident.getAssignee()), ref(incident.getService()), ref(incident.getCategory()),
                incident.getResolutionDeadline(), incident.getCreatedAt());
    }

    public IncidentDetailResponse toDetail(Incident incident) {
        Incident original = incident.getDuplicateOf();
        return new IncidentDetailResponse(incident.getId(), incident.getIncidentNumber(), incident.getTitle(),
                incident.getDescription(), ref(incident.getReporter()), ref(incident.getAssignee()),
                ref(incident.getService()), ref(incident.getCategory()), incident.getImpact(), incident.getUrgency(),
                incident.getAffectedUsers(), incident.getPriorityScore(), incident.getPriority(), incident.getStatus(),
                incident.getSlaStatus(), incident.getResponseDeadline(), incident.getResolutionDeadline(),
                incident.getSlaPausedAt(), incident.getEscalationLevel(), incident.getResolution(),
                original == null ? null : original.getIncidentNumber(), incident.getRespondedAt(),
                incident.getResolvedAt(), incident.getClosedAt(), incident.getCreatedAt(), incident.getUpdatedAt());
    }

    public CreateIncidentResponse toCreateResponse(CreateIncidentResult result) {
        return new CreateIncidentResponse(toDetail(result.incident()), result.possibleDuplicates());
    }

    public CommentResponse toComment(IncidentComment comment) {
        return new CommentResponse(comment.getId(), comment.getType(), comment.getBody(), ref(comment.getAuthor()),
                comment.getCreatedAt());
    }

    public AuditResponse toAudit(AuditLog line) {
        Incident incident = line.getIncident();
        return new AuditResponse(line.getId(), incident == null ? null : incident.getId(),
                incident == null ? null : incident.getIncidentNumber(), line.getAction(), line.getPreviousState(),
                line.getNewState(), line.getDescription(), ref(line.getUser()), line.getCreatedAt());
    }

    public NotificationResponse toNotification(Notification notification) {
        Incident incident = notification.getIncident();
        return new NotificationResponse(notification.getId(), incident == null ? null : incident.getId(),
                incident == null ? null : incident.getIncidentNumber(), notification.getEventType(),
                notification.getMessage(), notification.isRead(), notification.getCreatedAt());
    }

    public FeedbackResponse toFeedback(Feedback feedback) {
        return new FeedbackResponse(feedback.getId(), feedback.getIncident().getId(), feedback.getRating(),
                feedback.getComments(), feedback.getCreatedAt());
    }

    public TechnicianSummaryResponse toTechnician(Technician technician) {
        Team team = technician.getTeam();
        return new TechnicianSummaryResponse(technician.getId(), technician.getFullName(),
                team == null ? null : team.getName(), technician.getExperienceYears(), technician.getMaxWorkload(),
                technician.isAvailable());
    }

    public CategorySuggestionResponse toSuggestion(CategoryScore score) {
        return new CategorySuggestionResponse(score.category().getId(), score.category().getName(), score.hits());
    }

    // ------------------------------------------------------------------ users, teams and the catalog

    /**
     * A user with the fields of their kind. The user may be a lazy stand-in for the real object (which would not be
     * recognised as an employee or technician), so the real one is looked up first.
     */
    public UserResponse toUser(User user) {
        Object real = Hibernate.unproxy(user);
        String department = null;
        NamedReference team = null;
        Integer maxWorkload = null;
        Boolean available = null;
        Integer experienceYears = null;
        if (real instanceof Employee employee) {
            department = employee.getDepartment();
        } else if (real instanceof Technician technician) {
            Team technicianTeam = technician.getTeam();
            team = technicianTeam == null ? null : new NamedReference(technicianTeam.getId(), technicianTeam.getName());
            maxWorkload = technician.getMaxWorkload();
            available = technician.isAvailable();
            experienceYears = technician.getExperienceYears();
        }
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole(), user.isActive(),
                department, team, maxWorkload, available, experienceYears);
    }

    public TeamResponse toTeam(Team team) {
        return new TeamResponse(team.getId(), team.getName(), ref(team.getLead()));
    }

    public SkillResponse toSkill(Skill skill) {
        return new SkillResponse(skill.getId(), skill.getName());
    }

    public TechnicianSkillResponse toTechnicianSkill(TechnicianSkill link, Skill skill) {
        return new TechnicianSkillResponse(skill.getId(), skill.getName(), link.getProficiency());
    }

    public ServiceResponse toService(ITService service) {
        return new ServiceResponse(service.getId(), service.getName(), service.getDescription(),
                service.getBusinessCriticality(), service.isActive());
    }

    public CategoryResponse toCategory(Category category) {
        Skill skill = category.getRequiredSkill();
        return new CategoryResponse(category.getId(), category.getName(), category.getDescription(),
                category.getKeywords(), skill == null ? null : new NamedReference(skill.getId(), skill.getName()),
                category.isActive());
    }

    public SlaPolicyResponse toSlaPolicy(SlaPolicy policy) {
        return new SlaPolicyResponse(policy.getId(), policy.getPriority(), ref(policy.getService()),
                policy.getResponseMinutes(), policy.getResolutionMinutes());
    }

    // ------------------------------------------------------------------ references

    private static NamedReference ref(User user) {
        return user == null ? null : new NamedReference(user.getId(), user.getFullName());
    }

    private static NamedReference ref(ITService service) {
        return service == null ? null : new NamedReference(service.getId(), service.getName());
    }

    private static NamedReference ref(Category category) {
        return category == null ? null : new NamedReference(category.getId(), category.getName());
    }
}
