package com.iitsm.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PropertiesValidationTest {

    @Test
    void validSlaPropertiesAreAccepted() {
        assertDoesNotThrow(() -> new SlaProperties(1.0, 0.8, 30000));
        assertDoesNotThrow(() -> new SlaProperties(20, 1.0, 1));
    }

    @Test
    void invalidSlaPropertiesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SlaProperties(0, 0.8, 30000));
        assertThrows(IllegalArgumentException.class, () -> new SlaProperties(-1, 0.8, 30000));
        assertThrows(IllegalArgumentException.class, () -> new SlaProperties(1, 0, 30000));
        assertThrows(IllegalArgumentException.class, () -> new SlaProperties(1, 1.01, 30000));
        assertThrows(IllegalArgumentException.class, () -> new SlaProperties(1, 0.8, 0));
    }

    @Test
    void escalationTimeoutMustBePositive() {
        assertDoesNotThrow(() -> new EscalationProperties(5));
        assertThrows(IllegalArgumentException.class, () -> new EscalationProperties(0));
    }
}
