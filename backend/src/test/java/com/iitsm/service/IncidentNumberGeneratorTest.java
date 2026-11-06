package com.iitsm.service;

import com.iitsm.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

class IncidentNumberGeneratorTest extends AbstractIntegrationTest {

    @Autowired IncidentNumberGenerator generator;
    @Autowired JdbcTemplate jdbc;

    @Test
    void numbersHaveIncPrefixAndIncreaseByOne() {
        String first = generator.next();
        String second = generator.next();

        assertTrue(first.matches("INC\\d+"), first);
        assertTrue(second.matches("INC\\d+"), second);
        assertEquals(number(first) + 1, number(second));
        assertTrue(number(first) >= 1001);
    }

    @Test
    void sequenceStartsAt1001() {
        Long start = jdbc.queryForObject(
                "select start_value from pg_sequences where sequencename = 'incident_number_seq'", Long.class);
        assertEquals(1001L, start);
    }

    @Test
    void duplicateThresholdWasLoweredTo0Point3() {
        String value = jdbc.queryForObject(
                "select config_value from system_config where config_key = 'duplicate.threshold'", String.class);
        assertEquals("0.3", value);
    }

    private static long number(String incidentNumber) {
        return Long.parseLong(incidentNumber.substring(3));
    }
}
