package com.iitsm.controller;

import com.iitsm.domain.user.Role;
import com.iitsm.dto.AdminRequests;
import com.iitsm.dto.PageResponse;
import com.iitsm.dto.TeamResponse;
import com.iitsm.dto.TechnicianSkillResponse;
import com.iitsm.dto.UserResponse;
import com.iitsm.service.UserAdminFacade;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Administrators manage users, teams, technicians and their skills. Everything here is for administrators only. */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserAdminFacade admin;

    public AdminUserController(UserAdminFacade admin) {
        this.admin = admin;
    }

    // ------------------------------------------------------------------ users

    /** Any role can be created here (unlike self-registration, which only creates employees). */
    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@Valid @RequestBody AdminRequests.CreateUser request) {
        return admin.createUser(request);
    }

    /** In name order, optionally only one {@code role}. Paging: {@code page} from 0, {@code size} (default 20, at most 100). */
    @GetMapping("/users")
    public PageResponse<UserResponse> listUsers(@RequestParam(required = false) Role role,
                                                @PageableDefault(size = 20) Pageable pageable) {
        return admin.listUsers(role, pageable);
    }

    @GetMapping("/users/{id}")
    public UserResponse getUser(@PathVariable Long id) {
        return admin.getUser(id);
    }

    @PutMapping("/users/{id}")
    public UserResponse updateUser(@PathVariable Long id, @Valid @RequestBody AdminRequests.UpdateUser request) {
        return admin.updateUser(id, request);
    }

    @PutMapping("/users/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setPassword(@PathVariable Long id, @Valid @RequestBody AdminRequests.NewPassword request) {
        admin.setPassword(id, request.password());
    }

    // ------------------------------------------------------------------ teams

    @GetMapping("/teams")
    public List<TeamResponse> teams() {
        return admin.teams();
    }

    @PostMapping("/teams")
    @ResponseStatus(HttpStatus.CREATED)
    public TeamResponse createTeam(@Valid @RequestBody AdminRequests.CreateTeam request) {
        return admin.createTeam(request.name());
    }

    @PutMapping("/teams/{id}/lead")
    public TeamResponse setTeamLead(@PathVariable Long id, @Valid @RequestBody AdminRequests.SetTeamLead request) {
        return admin.setTeamLead(id, request.leadId());
    }

    // ------------------------------------------------------------------ technicians

    @PutMapping("/technicians/{id}")
    public UserResponse updateTechnician(@PathVariable Long id, @Valid @RequestBody AdminRequests.UpdateTechnician request) {
        return admin.updateTechnician(id, request);
    }

    @GetMapping("/technicians/{id}/skills")
    public List<TechnicianSkillResponse> technicianSkills(@PathVariable Long id) {
        return admin.technicianSkills(id);
    }

    /** Replaces the technician's whole set of skills with the one in the body. */
    @PutMapping("/technicians/{id}/skills")
    public List<TechnicianSkillResponse> replaceSkills(@PathVariable Long id, @Valid @RequestBody AdminRequests.ReplaceSkills request) {
        return admin.replaceSkills(id, request);
    }
}
