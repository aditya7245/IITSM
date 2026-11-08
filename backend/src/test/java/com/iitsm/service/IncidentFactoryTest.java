package com.iitsm.service;

import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentStatus;
import com.iitsm.domain.user.User;
import com.iitsm.exception.InvalidInputException;
import com.iitsm.exception.ServiceNotFoundException;
import com.iitsm.repository.CategoryRepository;
import com.iitsm.repository.ITServiceRepository;
import com.iitsm.repository.UserRepository;
import com.iitsm.support.AbstractIntegrationTest;
import com.iitsm.support.MutableClock;
import com.iitsm.support.TestClockConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/** The factory with the real seeded services and categories. Rolls back after each test. */
@Transactional
class IncidentFactoryTest extends AbstractIntegrationTest {

    @Autowired IncidentFactory factory;
    @Autowired UserRepository users;
    @Autowired ITServiceRepository services;
    @Autowired CategoryRepository categories;
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    User reporter;
    Long paymentServiceId;
    Long applicationCategoryId;

    @BeforeEach
    void setUp() {
        clock.reset();
        reporter = users.findByEmailIgnoreCase("emp1@iitsm.local").orElseThrow();
        paymentServiceId = services.findByName("Payment Application").orElseThrow().getId();
        applicationCategoryId = categories.findByName("Application").orElseThrow().getId();
    }

    private CreateIncidentCommand command(String title, String description, Long categoryId, Long serviceId,
                                          int impact, int urgency, int affectedUsers) {
        return new CreateIncidentCommand(title, description, categoryId, serviceId,
                (short) impact, (short) urgency, affectedUsers);
    }

    private CreateIncidentCommand valid() {
        return command("Payment app down", "Users cannot complete payments", applicationCategoryId,
                paymentServiceId, 3, 3, 250);
    }

    private long lastSequenceValue() {
        return jdbc.queryForObject("select last_value from incident_number_seq", Long.class);
    }

    private void assertInvalid(CreateIncidentCommand command, String messagePart) {
        long before = lastSequenceValue();
        InvalidInputException e = assertThrows(InvalidInputException.class, () -> factory.create(command, reporter));
        assertTrue(e.getMessage().toLowerCase().contains(messagePart.toLowerCase()), e.getMessage());
        assertEquals(before, lastSequenceValue(), "a rejected report must not use up an incident number");
    }

    // ---------- the valid case ----------

    @Test
    void validCommandGivesANewUnsavedIncidentWithAllTheDetails() {
        Incident incident = factory.create(valid(), reporter);

        assertTrue(incident.getIncidentNumber().matches("INC\\d+"), incident.getIncidentNumber());
        assertEquals("Payment app down", incident.getTitle());
        assertEquals("Users cannot complete payments", incident.getDescription());
        assertEquals(reporter.getId(), incident.getReporter().getId());
        assertEquals(paymentServiceId, incident.getService().getId());
        assertEquals(applicationCategoryId, incident.getCategory().getId());
        assertEquals(3, incident.getImpact());
        assertEquals(3, incident.getUrgency());
        assertEquals(250, incident.getAffectedUsers());
        assertEquals(IncidentStatus.NEW, incident.getStatus());
        assertNull(incident.getId());                                   // not saved yet
        assertEquals(TestClockConfig.START, incident.getCreatedAt());   // taken from the injected clock
    }

    @Test
    void eachIncidentGetsTheNextNumber() {
        Incident first = factory.create(valid(), reporter);
        Incident second = factory.create(valid(), reporter);
        long a = Long.parseLong(first.getIncidentNumber().substring(3));
        long b = Long.parseLong(second.getIncidentNumber().substring(3));
        assertEquals(a + 1, b);
    }

    @Test
    void titleAndDescriptionAreTrimmed() {
        Incident incident = factory.create(
                command("  Payment app down  ", "  details  ", applicationCategoryId, paymentServiceId, 2, 2, 1), reporter);
        assertEquals("Payment app down", incident.getTitle());
        assertEquals("details", incident.getDescription());
    }

    // ---------- title and description ----------

    @Test
    void aMissingOrBlankTitleIsRejected() {
        assertInvalid(command(null, "d", applicationCategoryId, paymentServiceId, 2, 2, 1), "title");
        assertInvalid(command("   ", "d", applicationCategoryId, paymentServiceId, 2, 2, 1), "title");
    }

    @Test
    void aTitleOfExactly200CharactersIsAcceptedAnd201IsRejected() {
        assertDoesNotThrow(() -> factory.create(
                command("x".repeat(200), "d", applicationCategoryId, paymentServiceId, 2, 2, 1), reporter));
        assertInvalid(command("x".repeat(201), "d", applicationCategoryId, paymentServiceId, 2, 2, 1), "200");
    }

    @Test
    void aMissingOrBlankDescriptionIsRejected() {
        assertInvalid(command("t", null, applicationCategoryId, paymentServiceId, 2, 2, 1), "description");
        assertInvalid(command("t", "  ", applicationCategoryId, paymentServiceId, 2, 2, 1), "description");
    }

    // ---------- impact, urgency, affected users ----------

    @Test
    void impactAndUrgencyMustBeBetween1And3() {
        assertDoesNotThrow(() -> factory.create(command("t", "d", applicationCategoryId, paymentServiceId, 1, 1, 1), reporter));
        assertDoesNotThrow(() -> factory.create(command("t", "d", applicationCategoryId, paymentServiceId, 3, 3, 1), reporter));
        assertInvalid(command("t", "d", applicationCategoryId, paymentServiceId, 0, 2, 1), "impact");
        assertInvalid(command("t", "d", applicationCategoryId, paymentServiceId, 4, 2, 1), "impact");
        assertInvalid(command("t", "d", applicationCategoryId, paymentServiceId, 2, 0, 1), "urgency");
        assertInvalid(command("t", "d", applicationCategoryId, paymentServiceId, 2, 4, 1), "urgency");
    }

    @Test
    void affectedUsersMustBeAtLeastOne() {
        assertInvalid(command("t", "d", applicationCategoryId, paymentServiceId, 2, 2, 0), "affected users");
        assertInvalid(command("t", "d", applicationCategoryId, paymentServiceId, 2, 2, -5), "affected users");
    }

    // ---------- service and category ----------

    @Test
    void anUnknownServiceIsReportedAsNotFound() {
        long before = lastSequenceValue();
        ServiceNotFoundException e = assertThrows(ServiceNotFoundException.class, () -> factory.create(
                command("t", "d", applicationCategoryId, 999_999L, 2, 2, 1), reporter));
        assertTrue(e.getMessage().contains("999999"));
        assertEquals(before, lastSequenceValue());
    }

    @Test
    void anInactiveServiceIsReportedAsNotFound() {
        Long wiki = services.findByName("Internal Wiki").orElseThrow().getId();
        jdbc.update("update it_services set active = false where id = ?", wiki);
        em.clear();

        assertThrows(ServiceNotFoundException.class, () -> factory.create(
                command("t", "d", applicationCategoryId, wiki, 2, 2, 1), reporter));
    }

    @Test
    void aMissingServiceIsRejectedAsInvalidInput() {
        assertInvalid(command("t", "d", applicationCategoryId, null, 2, 2, 1), "service");
    }

    @Test
    void aMissingUnknownOrInactiveCategoryIsRejected() {
        assertInvalid(command("t", "d", null, paymentServiceId, 2, 2, 1), "category");
        assertInvalid(command("t", "d", 999_999L, paymentServiceId, 2, 2, 1), "category");

        Long hardware = categories.findByName("Hardware").orElseThrow().getId();
        jdbc.update("update categories set active = false where id = ?", hardware);
        em.clear();
        assertInvalid(command("t", "d", hardware, paymentServiceId, 2, 2, 1), "category");
    }

    @Test
    void aMissingReporterIsAProgrammingError() {
        assertThrows(NullPointerException.class, () -> factory.create(valid(), null));
        assertThrows(NullPointerException.class, () -> factory.create(null, reporter));
    }
}
