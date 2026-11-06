package com.iitsm;

import com.iitsm.config.EscalationProperties;
import com.iitsm.config.SlaProperties;
import com.iitsm.support.AbstractIntegrationTest;
import com.iitsm.support.MutableClock;
import com.iitsm.support.TestClockConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/** Checks that the shared test infrastructure and the new settings classes are wired correctly. */
class InfrastructureWiringTest extends AbstractIntegrationTest {

    @Autowired Clock clock;
    @Autowired MutableClock mutableClock;
    @Autowired SlaProperties sla;
    @Autowired EscalationProperties escalation;

    @BeforeEach
    void resetClock() {
        mutableClock.reset();
    }

    @Test
    void injectedClockIsTheMutableClockInTests() {
        assertSame(mutableClock, clock);
        assertEquals(TestClockConfig.START, clock.instant());
    }

    @Test
    void advancingTheMutableClockIsVisibleThroughTheInjectedClock() {
        mutableClock.advance(Duration.ofMinutes(10));
        assertEquals(TestClockConfig.START.plus(Duration.ofMinutes(10)), clock.instant());
    }

    @Test
    void slaPropertiesBindFromApplicationYml() {
        assertEquals(1.0, sla.timeScale());
        assertEquals(0.8, sla.warningThreshold());
        assertEquals(30000, sla.monitorIntervalMs());
    }

    @Test
    void escalationPropertiesBindFromApplicationYml() {
        assertEquals(5, escalation.levelTimeoutMinutes());
    }
}
