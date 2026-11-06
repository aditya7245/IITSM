package com.iitsm.support;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class MutableClockTest {

    private static final Instant START = Instant.parse("2026-11-10T09:00:00Z");

    @Test
    void startsAtTheGivenInstant() {
        assertEquals(START, new MutableClock(START).instant());
    }

    @Test
    void advanceMovesTimeForwardAndAccumulates() {
        MutableClock clock = new MutableClock(START);
        clock.advance(Duration.ofMinutes(5));
        clock.advance(Duration.ofSeconds(30));
        assertEquals(START.plusSeconds(330), clock.instant());
    }

    @Test
    void setJumpsToTheGivenInstant() {
        MutableClock clock = new MutableClock(START);
        Instant later = START.plus(Duration.ofDays(1));
        clock.set(later);
        assertEquals(later, clock.instant());
    }

    @Test
    void resetReturnsToTheStartingInstant() {
        MutableClock clock = new MutableClock(START);
        clock.advance(Duration.ofHours(3));
        clock.reset();
        assertEquals(START, clock.instant());
    }

    @Test
    void millisFollowsInstant() {
        MutableClock clock = new MutableClock(START);
        clock.advance(Duration.ofSeconds(1));
        assertEquals(START.plusSeconds(1).toEpochMilli(), clock.millis());
    }

    @Test
    void withZoneKeepsTheCurrentTimeButIsIndependent() {
        MutableClock clock = new MutableClock(START);
        var copy = clock.withZone(ZoneId.of("Asia/Kolkata"));
        clock.advance(Duration.ofMinutes(1));
        assertEquals(START, copy.instant());
        assertEquals(ZoneId.of("Asia/Kolkata"), copy.getZone());
    }
}
