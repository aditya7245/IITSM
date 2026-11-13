package com.iitsm.service;

import com.iitsm.domain.catalog.Skill;
import com.iitsm.domain.catalog.TechnicianSkill;
import com.iitsm.domain.user.Administrator;
import com.iitsm.domain.user.Employee;
import com.iitsm.domain.user.ITManager;
import com.iitsm.domain.user.Role;
import com.iitsm.domain.user.Team;
import com.iitsm.domain.user.TeamLead;
import com.iitsm.domain.user.Technician;
import com.iitsm.domain.user.User;
import com.iitsm.dto.AdminRequests;
import com.iitsm.dto.PageResponse;
import com.iitsm.dto.RegisterRequest;
import com.iitsm.dto.ResponseMapper;
import com.iitsm.dto.TeamResponse;
import com.iitsm.dto.TechnicianSkillResponse;
import com.iitsm.dto.UserResponse;
import com.iitsm.exception.DuplicateResourceException;
import com.iitsm.exception.InvalidInputException;
import com.iitsm.exception.ResourceNotFoundException;
import com.iitsm.exception.UnauthorizedOperationException;
import com.iitsm.repository.SkillRepository;
import com.iitsm.repository.TeamRepository;
import com.iitsm.repository.TechnicianRepository;
import com.iitsm.repository.TechnicianSkillRepository;
import com.iitsm.repository.UserRepository;
import com.iitsm.security.CurrentUserService;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Accounts for the controllers: signing up, the logged-in user, and everything an administrator does with users,
 * teams, technicians and their skills. Each method is one transaction that returns response objects. Who may call
 * the administration methods is declared on the controller.
 */
@Service
@Transactional
public class UserAdminFacade {

    static final int DEFAULT_MAX_WORKLOAD = 5;

    private final UserRepository users;
    private final TeamRepository teams;
    private final TechnicianRepository technicians;
    private final SkillRepository skills;
    private final TechnicianSkillRepository technicianSkills;
    private final UserFactory userFactory;
    private final CurrentUserService currentUser;
    private final ResponseMapper mapper;

    public UserAdminFacade(UserRepository users, TeamRepository teams, TechnicianRepository technicians,
                           SkillRepository skills, TechnicianSkillRepository technicianSkills,
                           UserFactory userFactory, CurrentUserService currentUser, ResponseMapper mapper) {
        this.users = users;
        this.teams = teams;
        this.technicians = technicians;
        this.skills = skills;
        this.technicianSkills = technicianSkills;
        this.userFactory = userFactory;
        this.currentUser = currentUser;
        this.mapper = mapper;
    }

    // ------------------------------------------------------------------ signing up and "who am I"

    /** Public sign-up. Always creates an employee, whatever else the request says. */
    public UserResponse register(RegisterRequest request) {
        return mapper.toUser(createAndSave(new CreateUserCommand(request.email(), request.password(),
                request.fullName(), Role.EMPLOYEE, request.department(), null, 0, 0)));
    }

    @Transactional(readOnly = true)
    public UserResponse me() {
        return mapper.toUser(currentUser.requireUser());
    }

    /** A technician (or team lead) marks themselves available or not for new assignments. */
    public UserResponse setMyAvailability(boolean available) {
        Long id = currentUser.requireUser().getId();
        Technician technician = technicians.findById(id).orElseThrow(UnauthorizedOperationException::new);
        technician.setAvailable(available);
        return mapper.toUser(technician);
    }

    // ------------------------------------------------------------------ users

    /** Creates a user of any role. Only technicians and team leads can be given a team. */
    public UserResponse createUser(AdminRequests.CreateUser request) {
        boolean technician = request.role() == Role.TECHNICIAN || request.role() == Role.TEAM_LEAD;
        if (request.teamId() != null && !technician) {
            throw new InvalidInputException("Only technicians and team leads can belong to a team");
        }
        Team team = request.teamId() == null ? null : team(request.teamId());
        int maxWorkload = request.maxWorkload() == null ? DEFAULT_MAX_WORKLOAD : request.maxWorkload();
        int experienceYears = request.experienceYears() == null ? 0 : request.experienceYears();
        return mapper.toUser(createAndSave(new CreateUserCommand(request.email(), request.password(),
                request.fullName(), request.role(), request.department(), team, maxWorkload, experienceYears)));
    }

    /** One page of users in name order, optionally only those of one role. */
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> listUsers(Role role, Pageable requested) {
        Pageable pageable = PageRequests.ordered(requested, Sort.by(Sort.Direction.ASC, "fullName", "id"));
        Page<User> page = role == null ? users.findAll(pageable) : users.findPageByExactType(classOf(role), pageable);
        return PageResponse.from(page, mapper::toUser);
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(Long id) {
        return mapper.toUser(user(id));
    }

    /**
     * Changes the name, switches the account on or off, and (for an employee) the department.
     * An administrator cannot switch off their own account, so the system cannot be left without one by mistake.
     */
    public UserResponse updateUser(Long id, AdminRequests.UpdateUser request) {
        User user = user(id);
        if (!request.active() && currentUser.requireUser().getId().equals(id)) {
            throw new InvalidInputException("You cannot deactivate your own account");
        }
        user.rename(request.fullName().strip());
        if (request.active()) {
            user.activate();
        } else {
            user.deactivate();
        }
        if (Hibernate.unproxy(user) instanceof Employee employee) {
            employee.changeDepartment(request.department() == null || request.department().isBlank()
                    ? null : request.department().strip());
        }
        return mapper.toUser(user);
    }

    public void setPassword(Long id, String newPassword) {
        user(id).changePasswordHash(userFactory.encodePassword(newPassword));
    }

    // ------------------------------------------------------------------ teams

    @Transactional(readOnly = true)
    public List<TeamResponse> teams() {
        return teams.findAll().stream()
                .sorted(Comparator.comparing(Team::getName, String.CASE_INSENSITIVE_ORDER))
                .map(mapper::toTeam).toList();
    }

    public TeamResponse createTeam(String name) {
        String clean = name.strip();
        if (teams.findByNameIgnoreCase(clean).isPresent()) {
            throw new DuplicateResourceException("A team named '" + clean + "' already exists");
        }
        return mapper.toTeam(teams.save(new Team(clean)));
    }

    /**
     * Makes a team lead the lead of this team. The person must be an active team lead who already belongs to the
     * team, so that an escalation to "the team's lead" always reaches someone in that team.
     */
    public TeamResponse setTeamLead(Long teamId, Long leadId) {
        Team team = team(teamId);
        if (!(Hibernate.unproxy(user(leadId)) instanceof TeamLead lead)) {
            throw new InvalidInputException("The lead must be a user with the team lead role");
        }
        if (!lead.isActive()) {
            throw new InvalidInputException("The lead's account is not active");
        }
        if (lead.getTeam() == null || !lead.getTeam().getId().equals(team.getId())) {
            throw new InvalidInputException(lead.getFullName() + " is not a member of " + team.getName()
                    + "; move them into the team first");
        }
        team.assignLead(lead);
        return mapper.toTeam(team);
    }

    // ------------------------------------------------------------------ technicians and their skills

    /**
     * Changes a technician's limits, availability and team. A team lead cannot be moved out of the team they lead.
     */
    public UserResponse updateTechnician(Long id, AdminRequests.UpdateTechnician request) {
        Technician technician = technician(id);
        Team newTeam = request.teamId() == null ? null : team(request.teamId());
        Long oldTeamId = technician.getTeam() == null ? null : technician.getTeam().getId();
        Long newTeamId = newTeam == null ? null : newTeam.getId();
        if (!Objects.equals(oldTeamId, newTeamId)) {
            teams.findByLeadId(id).ifPresent(led -> {
                throw new InvalidInputException(technician.getFullName() + " leads the team '" + led.getName()
                        + "'; choose another lead before moving them");
            });
        }
        technician.changeMaxWorkload(request.maxWorkload());
        technician.changeExperienceYears(request.experienceYears());
        technician.setAvailable(request.available());
        technician.moveToTeam(newTeam);
        return mapper.toUser(technician);
    }

    @Transactional(readOnly = true)
    public List<TechnicianSkillResponse> technicianSkills(Long id) {
        technician(id);
        return skillResponses(technicianSkills.findByTechnicianIdIn(List.of(id)));
    }

    /**
     * Replaces the technician's whole set of skills with the one given (an empty list removes them all).
     * A skill can appear only once in the request, and every skill must exist.
     */
    public List<TechnicianSkillResponse> replaceSkills(Long id, AdminRequests.ReplaceSkills request) {
        Technician technician = technician(id);
        Set<Long> seen = new HashSet<>();
        for (AdminRequests.SkillLevel level : request.skills()) {
            if (!seen.add(level.skillId())) {
                throw new InvalidInputException("Skill " + level.skillId() + " is listed more than once");
            }
            if (!skills.existsById(level.skillId())) {
                throw new ResourceNotFoundException("Skill " + level.skillId() + " not found");
            }
        }
        List<TechnicianSkill> old = technicianSkills.findByTechnicianIdIn(List.of(technician.getId()));
        technicianSkills.deleteAll(old);
        technicianSkills.flush();                       // the deletes must reach the database before the inserts
        List<TechnicianSkill> replacement = request.skills().stream()
                .map(level -> new TechnicianSkill(technician.getId(), level.skillId(), level.proficiency().shortValue()))
                .toList();
        return skillResponses(technicianSkills.saveAll(replacement));
    }

    // ------------------------------------------------------------------ helpers

    /** Checks the new user's details, makes sure the email is free, and saves. */
    private User createAndSave(CreateUserCommand command) {
        User user = userFactory.create(command);
        if (users.existsByEmailIgnoreCase(user.getEmail())) {
            throw new DuplicateResourceException("An account with the email " + user.getEmail() + " already exists");
        }
        return users.save(user);
    }

    private List<TechnicianSkillResponse> skillResponses(List<TechnicianSkill> links) {
        Map<Long, Skill> byId = skills.findAll().stream().collect(Collectors.toMap(Skill::getId, Function.identity()));
        return links.stream()
                .map(link -> mapper.toTechnicianSkill(link, byId.get(link.getSkillId())))
                .sorted(Comparator.comparing(TechnicianSkillResponse::skillName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private User user(Long id) {
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User " + id + " not found"));
    }

    private Team team(Long id) {
        return teams.findById(id).orElseThrow(() -> new ResourceNotFoundException("Team " + id + " not found"));
    }

    private Technician technician(Long id) {
        return technicians.findById(id).orElseThrow(() -> new ResourceNotFoundException("Technician " + id + " not found"));
    }

    private static Class<? extends User> classOf(Role role) {
        return switch (role) {
            case EMPLOYEE -> Employee.class;
            case TECHNICIAN -> Technician.class;
            case TEAM_LEAD -> TeamLead.class;
            case ADMINISTRATOR -> Administrator.class;
            case IT_MANAGER -> ITManager.class;
        };
    }
}
