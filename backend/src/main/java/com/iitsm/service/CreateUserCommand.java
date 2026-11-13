package com.iitsm.service;

import com.iitsm.domain.user.Role;
import com.iitsm.domain.user.Team;

/**
 * Everything needed to create a user of any role. Only the fields that make sense for the role are used:
 * {@code department} for an employee; {@code team}, {@code maxWorkload} and {@code experienceYears} for a
 * technician or team lead. The password is the plain text; the factory turns it into a hash.
 */
public record CreateUserCommand(String email, String password, String fullName, Role role, String department,
                                Team team, int maxWorkload, int experienceYears) { }
