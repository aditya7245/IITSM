package com.iitsm.service;

import com.iitsm.domain.user.Administrator;
import com.iitsm.domain.user.Employee;
import com.iitsm.domain.user.ITManager;
import com.iitsm.domain.user.Role;
import com.iitsm.domain.user.Technician;
import com.iitsm.domain.user.TeamLead;
import com.iitsm.domain.user.User;
import com.iitsm.exception.InvalidInputException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Builds the right kind of user for a role (the Factory pattern) and never stores a plain password: it is
 * hashed here. The new user is not saved.
 */
@Component
public class UserFactory {

    static final int MIN_PASSWORD_LENGTH = 8;
    /** BCrypt only looks at the first 72 characters, so longer passwords would be silently cut. */
    static final int MAX_PASSWORD_LENGTH = 72;

    private final PasswordEncoder encoder;

    public UserFactory(PasswordEncoder encoder) {
        this.encoder = encoder;
    }

    /**
     * @throws InvalidInputException if the email, name, password or (for technicians) the numbers are not valid
     */
    public User create(CreateUserCommand command) {
        if (command.role() == null) {
            throw new InvalidInputException("A role is required");
        }
        String email = requireText(command.email(), "Email", 150);
        if (!email.matches("[^@\\s]+@[^@\\s]+")) {
            throw new InvalidInputException("Email is not a valid address");
        }
        String fullName = requireText(command.fullName(), "Name", 120);
        String department = command.department() == null || command.department().isBlank()
                ? null : command.department().strip();
        if (department != null && department.length() > 100) {
            throw new InvalidInputException("Department must be at most 100 characters");
        }
        boolean technician = command.role() == Role.TECHNICIAN || command.role() == Role.TEAM_LEAD;
        if (technician) {
            if (command.maxWorkload() < 1) {
                throw new InvalidInputException("Maximum workload must be at least 1");
            }
            if (command.experienceYears() < 0) {
                throw new InvalidInputException("Experience cannot be negative");
            }
        }
        String hash = encodePassword(command.password());

        return switch (command.role()) {
            case EMPLOYEE -> new Employee(email, hash, fullName, department);
            case TECHNICIAN -> new Technician(email, hash, fullName, command.team(), command.maxWorkload(), command.experienceYears());
            case TEAM_LEAD -> new TeamLead(email, hash, fullName, command.team(), command.maxWorkload(), command.experienceYears());
            case ADMINISTRATOR -> new Administrator(email, hash, fullName);
            case IT_MANAGER -> new ITManager(email, hash, fullName);
        };
    }

    /** Checks a plain password (8 to 72 characters) and returns its hash. */
    public String encodePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new InvalidInputException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (password.length() > MAX_PASSWORD_LENGTH) {
            throw new InvalidInputException("Password must be at most " + MAX_PASSWORD_LENGTH + " characters");
        }
        return encoder.encode(password);
    }

    private static String requireText(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new InvalidInputException(label + " is required");
        }
        String text = value.strip();
        if (text.length() > maxLength) {
            throw new InvalidInputException(label + " must be at most " + maxLength + " characters");
        }
        return text;
    }
}
