package com.iitsm.repository;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plain unit tests of the one pure part of {@link IncidentSpecifications}: the LIKE pattern for the incident number.
 * The conditions themselves are tested against the real database through the search endpoint.
 */
class IncidentSpecificationsTest {

    @Test
    void thePatternIsLowerCaseWithAWildcardOnBothSides() {
        assertEquals("%inc1014%", IncidentSpecifications.likePattern("INC1014"));
        assertEquals("%nc10%", IncidentSpecifications.likePattern("Nc10"));
    }

    @Test
    void percentAndUnderscoreMeanThemselvesNotWildcards() {
        assertEquals("%\\%%", IncidentSpecifications.likePattern("%"));
        assertEquals("%\\_%", IncidentSpecifications.likePattern("_"));
        assertEquals("%a\\%b\\_c%", IncidentSpecifications.likePattern("a%b_c"));
    }

    @Test
    void aBackslashIsEscapedToo() {
        assertEquals("%\\\\%", IncidentSpecifications.likePattern("\\"));
    }
}
