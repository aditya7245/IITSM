package com.iitsm.domain.sla;

import com.iitsm.domain.incident.Priority;
import com.iitsm.exception.InvalidInputException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SlaPolicyValidationTest {

    @Test
    void validPolicyKeepsItsValues() {
        SlaPolicy p = new SlaPolicy(Priority.P1, null, 5, 30);
        assertEquals(Priority.P1, p.getPriority());
        assertEquals(5, p.getResponseMinutes());
        assertEquals(30, p.getResolutionMinutes());
        assertNull(p.getService());
    }

    @Test
    void minutesMustBePositive() {
        assertThrows(InvalidInputException.class, () -> new SlaPolicy(Priority.P1, null, 0, 30));
        assertThrows(InvalidInputException.class, () -> new SlaPolicy(Priority.P1, null, 5, 0));
        assertThrows(InvalidInputException.class, () -> new SlaPolicy(Priority.P1, null, -5, 30));
    }

    @Test
    void priorityIsRequired() {
        assertThrows(InvalidInputException.class, () -> new SlaPolicy(null, null, 5, 30));
    }
}
