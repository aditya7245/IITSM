package com.iitsm.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** Plain unit tests of {@link Days}: dates become exact UTC times, and "to" includes the whole day. */
class DaysTest {

    @Test
    void aDayStartsAtMidnightUtc() {
        assertEquals(Instant.parse("2026-11-12T00:00:00Z"), Days.startOf(LocalDate.of(2026, 11, 12)));
    }

    @Test
    void theNextDayStartsTwentyFourHoursLater() {
        assertEquals(Instant.parse("2026-11-13T00:00:00Z"), Days.startOfNextDay(LocalDate.of(2026, 11, 12)));
    }

    @Test
    void theNextDayCrossesMonthAndYearEnds() {
        assertEquals(Instant.parse("2026-12-01T00:00:00Z"), Days.startOfNextDay(LocalDate.of(2026, 11, 30)));
        assertEquals(Instant.parse("2027-01-01T00:00:00Z"), Days.startOfNextDay(LocalDate.of(2026, 12, 31)));
        assertEquals(Instant.parse("2028-02-29T00:00:00Z"), Days.startOfNextDay(LocalDate.of(2028, 2, 28)));   // leap year
    }

    @Test
    void anInstantLateInTheDayIsBeforeTheStartOfTheNextDayButAtMidnightItIsNot() {
        Instant lastMoment = Instant.parse("2026-11-12T23:59:59.999Z");
        Instant midnight = Instant.parse("2026-11-13T00:00:00Z");
        LocalDate day = LocalDate.of(2026, 11, 12);

        assertTrue(lastMoment.isBefore(Days.startOfNextDay(day)));
        assertFalse(midnight.isBefore(Days.startOfNextDay(day)));
        assertFalse(lastMoment.isBefore(Days.startOf(day)));
    }
}
