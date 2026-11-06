package com.iitsm.domain.catalog;

import com.iitsm.repository.*;
import com.iitsm.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Loads the seeded reference data through the new entities; this also makes Hibernate validate their column types. */
@Transactional
class ReferenceDataMappingTest extends AbstractIntegrationTest {

    @Autowired SkillRepository skills;
    @Autowired CategoryRepository categories;
    @Autowired ITServiceRepository services;
    @Autowired TechnicianSkillRepository technicianSkills;
    @Autowired UserRepository users;

    @Test
    void seededCategoriesLoadWithKeywordsAndRequiredSkill() {
        List<Category> active = categories.findByActiveTrue();
        assertEquals(9, active.size());

        Category application = categories.findByName("Application").orElseThrow();
        assertTrue(application.getKeywords().contains("payment"));
        assertEquals("Application Support", application.getRequiredSkill().getName());
    }

    @Test
    void seededServicesLoadWithCriticality() {
        assertEquals(4, services.findByActiveTrue().size());
        assertEquals(5, services.findByName("Payment Application").orElseThrow().getBusinessCriticality());
        assertEquals(1, services.findByName("Internal Wiki").orElseThrow().getBusinessCriticality());
    }

    @Test
    void seededTechnicianSkillsLoadForTech1() {
        Long tech1 = users.findByEmailIgnoreCase("tech1@iitsm.local").orElseThrow().getId();

        Map<String, Short> byName = technicianSkills.findByTechnicianIdIn(List.of(tech1)).stream()
                .collect(Collectors.toMap(
                        ts -> skills.findById(ts.getSkillId()).orElseThrow().getName(),
                        TechnicianSkill::getProficiency));

        assertEquals(2, byName.size());
        assertEquals((short) 5, byName.get("Application Support"));
        assertEquals((short) 3, byName.get("Database"));
    }

    @Test
    void skillsLoadByName() {
        assertEquals(9, skills.count());
        assertTrue(skills.findByName("Networking").isPresent());
    }
}
