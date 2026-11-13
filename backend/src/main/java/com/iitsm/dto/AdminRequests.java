package com.iitsm.dto;

import com.iitsm.domain.incident.Priority;
import com.iitsm.domain.user.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** The request bodies of the administration endpoints, kept together because each is only a few fields. */
public final class AdminRequests {

    private AdminRequests() { }

    /** Creating a user of any role. {@code maxWorkload} (default 5) and {@code experienceYears} (default 0) only matter for technicians and team leads. */
    public record CreateUser(
            @NotBlank @Email @Size(max = 150) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 120) String fullName,
            @NotNull Role role,
            @Size(max = 100) String department,
            Long teamId,
            @Min(1) Integer maxWorkload,
            @Min(0) Integer experienceYears) { }

    /** {@code department} only applies to employees. */
    public record UpdateUser(
            @NotBlank @Size(max = 120) String fullName,
            @NotNull Boolean active,
            @Size(max = 100) String department) { }

    public record NewPassword(@NotBlank @Size(min = 8, max = 72) String password) { }

    public record CreateTeam(@NotBlank @Size(max = 100) String name) { }

    public record SetTeamLead(@NotNull Long leadId) { }

    /** {@code teamId} null means no team. */
    public record UpdateTechnician(
            @NotNull @Min(1) Integer maxWorkload,
            @NotNull Boolean available,
            @NotNull @Min(0) Integer experienceYears,
            Long teamId) { }

    public record SkillLevel(@NotNull Long skillId, @NotNull @Min(1) @Max(5) Integer proficiency) { }

    /** The complete new set of skills; an empty list removes them all. */
    public record ReplaceSkills(@NotNull @Valid List<SkillLevel> skills) { }

    public record SetAvailability(@NotNull Boolean available) { }

    /** {@code active} null means active when creating and unchanged when updating. */
    public record ServiceRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 255) String description,
            @NotNull @Min(1) @Max(5) Integer businessCriticality,
            Boolean active) { }

    /** {@code keywords} is a comma-separated list of single words; {@code requiredSkillId} may be null. */
    public record CategoryRequest(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 255) String description,
            String keywords,
            Long requiredSkillId,
            Boolean active) { }

    /** {@code serviceId} null makes it the default policy for the priority. */
    public record CreateSlaPolicy(
            @NotNull Priority priority,
            Long serviceId,
            @NotNull @Min(1) Integer responseMinutes,
            @NotNull @Min(1) Integer resolutionMinutes) { }

    public record UpdateSlaPolicy(
            @NotNull @Min(1) Integer responseMinutes,
            @NotNull @Min(1) Integer resolutionMinutes) { }

    public record AssignmentStrategy(@NotBlank String strategy) { }
}
