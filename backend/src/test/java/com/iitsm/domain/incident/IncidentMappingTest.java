package com.iitsm.domain.incident;

import com.iitsm.domain.catalog.Category;
import com.iitsm.domain.catalog.ITService;
import com.iitsm.domain.user.User;
import com.iitsm.repository.*;
import com.iitsm.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/** Saves and reloads incidents so Hibernate checks every column mapping. Rolls back after each test. */
@Transactional
class IncidentMappingTest extends AbstractIntegrationTest {

    private static final Instant CREATED = Instant.parse("2026-11-10T09:00:00Z");

    @Autowired IncidentRepository incidents;
    @Autowired UserRepository users;
    @Autowired ITServiceRepository services;
    @Autowired CategoryRepository categories;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;

    User reporter;
    ITService service;
    Category category;

    @BeforeEach
    void loadReferenceData() {
        reporter = users.findByEmailIgnoreCase("emp1@iitsm.local").orElseThrow();
        service = services.findByName("Payment Application").orElseThrow();
        category = categories.findByName("Application").orElseThrow();
    }

    private Incident newIncident(String number) {
        return new Incident(number, "Payment app down", "Users cannot complete payments",
                reporter, service, category, (short) 3, (short) 3, 250, CREATED);
    }

    @Test
    void savedIncidentReloadsWithAllValuesAndDefaults() {
        Incident saved = incidents.saveAndFlush(newIncident("TEST-1"));
        em.clear();

        Incident loaded = incidents.findById(saved.getId()).orElseThrow();
        assertEquals("TEST-1", loaded.getIncidentNumber());
        assertEquals("Payment app down", loaded.getTitle());
        assertEquals("Users cannot complete payments", loaded.getDescription());
        assertEquals(reporter.getId(), loaded.getReporter().getId());
        assertEquals(service.getId(), loaded.getService().getId());
        assertEquals(category.getId(), loaded.getCategory().getId());
        assertEquals(3, loaded.getImpact());
        assertEquals(3, loaded.getUrgency());
        assertEquals(250, loaded.getAffectedUsers());

        // defaults
        assertEquals(IncidentStatus.NEW, loaded.getStatus());
        assertEquals(SlaStatus.ON_TRACK, loaded.getSlaStatus());
        assertEquals(0, loaded.getSlaPausedSeconds());
        assertEquals(0, loaded.getEscalationLevel());
        assertNull(loaded.getPriority());
        assertNull(loaded.getPriorityScore());
        assertNull(loaded.getAssignee());
        assertNull(loaded.getDuplicateOf());
        assertNull(loaded.getResolution());

        // the creation time we supplied is kept, and the update time is filled in
        assertEquals(CREATED, loaded.getCreatedAt());
        assertNotNull(loaded.getUpdatedAt());
    }

    @Test
    void canBeFoundByIncidentNumber() {
        incidents.saveAndFlush(newIncident("TEST-2"));
        assertTrue(incidents.findByIncidentNumber("TEST-2").isPresent());
        assertTrue(incidents.findByIncidentNumber("NOPE").isEmpty());
    }

    @Test
    void assigneeAndDuplicateOfMappingsLoad() {
        Incident original = incidents.saveAndFlush(newIncident("TEST-3"));
        Incident copy = incidents.saveAndFlush(newIncident("TEST-4"));
        Long tech1 = users.findByEmailIgnoreCase("tech1@iitsm.local").orElseThrow().getId();

        // No behaviour methods exist yet (item 2.4), so set these two links with SQL.
        jdbc.update("update incidents set assignee_id = ?, duplicate_of_id = ? where id = ?",
                tech1, original.getId(), copy.getId());
        em.clear();

        Incident loaded = incidents.findById(copy.getId()).orElseThrow();
        assertEquals(tech1, loaded.getAssignee().getId());
        assertEquals("TEST-3", loaded.getDuplicateOf().getIncidentNumber());
    }
}
