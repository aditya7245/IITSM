package com.iitsm.dto;

import com.iitsm.domain.user.Role;

/**
 * A user account (never the password). The last five fields depend on the kind of user and are null otherwise:
 * {@code department} is for employees; {@code team}, {@code maxWorkload}, {@code available} and
 * {@code experienceYears} are for technicians and team leads ({@code team} is also null for a technician without one).
 */
public record UserResponse(Long id, String email, String fullName, Role role, boolean active, String department,
                           NamedReference team, Integer maxWorkload, Boolean available, Integer experienceYears) { }
