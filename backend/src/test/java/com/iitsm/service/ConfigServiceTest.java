package com.iitsm.service;

import com.iitsm.exception.InvalidInputException;
import com.iitsm.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/** Each test rolls back, so changes made here never leak into other tests sharing the database. */
@Transactional
class ConfigServiceTest extends AbstractIntegrationTest {

    @Autowired ConfigService config;

    @Test
    void seededSettingsReadWithTheirTypes() {
        assertEquals("SKILL_BASED", config.getString(ConfigKeys.ASSIGNMENT_STRATEGY, "x"));
        assertEquals(0.3, config.getDouble(ConfigKeys.DUPLICATE_THRESHOLD, -1));
        assertEquals(24, config.getInt(ConfigKeys.DUPLICATE_WINDOW_HOURS, -1));
        assertEquals(100, config.getInt(ConfigKeys.PRIORITY_BUMP_USERS, -1));
    }

    @Test
    void missingSettingReturnsTheDefault() {
        assertEquals("fallback", config.getString("no.such.key", "fallback"));
        assertEquals(7, config.getInt("no.such.key", 7));
        assertEquals(0.5, config.getDouble("no.such.key", 0.5));
    }

    @Test
    void setChangesAnExistingSetting() {
        config.set(ConfigKeys.ASSIGNMENT_STRATEGY, "LOAD_BASED");
        assertEquals("LOAD_BASED", config.getString(ConfigKeys.ASSIGNMENT_STRATEGY, "x"));
    }

    @Test
    void setCreatesAMissingSetting() {
        config.set("demo.flag", "on");
        assertEquals("on", config.getString("demo.flag", "x"));
    }

    @Test
    void numberThatCannotBeParsedFallsBackToTheDefault() {
        config.set(ConfigKeys.DUPLICATE_WINDOW_HOURS, "abc");
        config.set(ConfigKeys.DUPLICATE_THRESHOLD, "abc");
        assertEquals(12, config.getInt(ConfigKeys.DUPLICATE_WINDOW_HOURS, 12));
        assertEquals(0.4, config.getDouble(ConfigKeys.DUPLICATE_THRESHOLD, 0.4));
    }

    @Test
    void booleanSettingsAreReadCaseInsensitivelyWithADefaultForBadOrMissingValues() {
        assertFalse(config.getBoolean(ConfigKeys.AUTO_ASSIGN_TEAM_LEADS, true));   // seeded by migration V5 as "false"
        config.set("demo.flag", " TRUE ");
        assertTrue(config.getBoolean("demo.flag", false));
        config.set("demo.flag", "maybe");
        assertTrue(config.getBoolean("demo.flag", true));
        assertFalse(config.getBoolean("demo.flag", false));
        assertTrue(config.getBoolean("no.such.key", true));
    }

    @Test
    void blankKeyOrValueIsRejected() {
        assertThrows(InvalidInputException.class, () -> config.set(" ", "v"));
        assertThrows(InvalidInputException.class, () -> config.set("k", " "));
        assertThrows(InvalidInputException.class, () -> config.set("k", null));
    }
}
