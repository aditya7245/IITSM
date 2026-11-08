package com.iitsm.service;

import com.iitsm.domain.catalog.Category;
import com.iitsm.domain.catalog.ITService;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.user.User;
import com.iitsm.exception.InvalidInputException;
import com.iitsm.exception.ServiceNotFoundException;
import com.iitsm.repository.CategoryRepository;
import com.iitsm.repository.ITServiceRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Objects;

/**
 * Factory pattern: turns a submitted report form into a valid new incident, or explains what is wrong with it.
 * All checks run before the incident number is taken, so rejected reports never use up a number.
 * The returned incident is NEW and not saved yet. Do not call this from a read-only transaction,
 * because taking a number writes to the database sequence.
 */
@Component
public class IncidentFactory {

    static final int MAX_TITLE_LENGTH = 200;

    private final ITServiceRepository services;
    private final CategoryRepository categories;
    private final IncidentNumberGenerator numbers;
    private final Clock clock;

    public IncidentFactory(ITServiceRepository services, CategoryRepository categories,
                           IncidentNumberGenerator numbers, Clock clock) {
        this.services = services;
        this.categories = categories;
        this.numbers = numbers;
        this.clock = clock;
    }

    /**
     * @throws InvalidInputException    if a field is missing or out of range, or the category is unknown or inactive
     * @throws ServiceNotFoundException if the service is unknown or inactive
     */
    public Incident create(CreateIncidentCommand command, User reporter) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(reporter, "reporter");

        String title = validTitle(command.title());
        String description = validDescription(command.description());
        requireLevel(command.impact(), "Impact");
        requireLevel(command.urgency(), "Urgency");
        if (command.affectedUsers() < 1) {
            throw new InvalidInputException("Affected users must be at least 1");
        }

        ITService service = findService(command.serviceId());
        Category category = findCategory(command.categoryId());

        return new Incident(numbers.next(), title, description, reporter, service, category,
                command.impact(), command.urgency(), command.affectedUsers(), clock.instant());
    }

    private static String validTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new InvalidInputException("Title is required");
        }
        String trimmed = title.trim();
        if (trimmed.length() > MAX_TITLE_LENGTH) {
            throw new InvalidInputException("Title must be at most " + MAX_TITLE_LENGTH + " characters");
        }
        return trimmed;
    }

    private static String validDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new InvalidInputException("Description is required");
        }
        return description.trim();
    }

    private static void requireLevel(short level, String name) {
        if (level < 1 || level > 3) {
            throw new InvalidInputException(name + " must be between 1 and 3");
        }
    }

    private ITService findService(Long serviceId) {
        if (serviceId == null) {
            throw new InvalidInputException("A service is required");
        }
        return services.findById(serviceId)
                .filter(ITService::isActive)
                .orElseThrow(() -> new ServiceNotFoundException(String.valueOf(serviceId)));
    }

    private Category findCategory(Long categoryId) {
        if (categoryId == null) {
            throw new InvalidInputException("A category is required");
        }
        return categories.findById(categoryId)
                .filter(Category::isActive)
                .orElseThrow(() -> new InvalidInputException("Unknown or inactive category: " + categoryId));
    }
}
