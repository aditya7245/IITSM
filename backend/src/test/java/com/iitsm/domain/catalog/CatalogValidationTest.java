package com.iitsm.domain.catalog;

import com.iitsm.exception.InvalidInputException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CatalogValidationTest {

    @Test
    void criticalityMustBeBetween1And5() {
        assertDoesNotThrow(() -> new ITService("A", "d", (short) 1));
        assertDoesNotThrow(() -> new ITService("A", "d", (short) 5));
        assertThrows(InvalidInputException.class, () -> new ITService("A", "d", (short) 0));
        assertThrows(InvalidInputException.class, () -> new ITService("A", "d", (short) 6));
    }

    @Test
    void proficiencyMustBeBetween1And5() {
        assertEquals(3, new TechnicianSkill(1L, 2L, (short) 3).getProficiency());
        assertThrows(InvalidInputException.class, () -> new TechnicianSkill(1L, 2L, (short) 0));
        assertThrows(InvalidInputException.class, () -> new TechnicianSkill(1L, 2L, (short) 6));
    }

    @Test
    void compositeKeyComparesByBothParts() {
        assertEquals(new TechnicianSkillId(1L, 2L), new TechnicianSkillId(1L, 2L));
        assertNotEquals(new TechnicianSkillId(1L, 2L), new TechnicianSkillId(1L, 3L));
        assertEquals(new TechnicianSkillId(1L, 2L).hashCode(), new TechnicianSkillId(1L, 2L).hashCode());
    }

    @Test
    void categoryWithNullKeywordsGetsEmptyString() {
        assertEquals("", new Category("X", null, null, null).getKeywords());
    }
}
