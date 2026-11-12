package com.iitsm.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Turns the calendar dates people type into filters ("from 12 November to 14 November, both days included")
 * into exact times. Days are counted in UTC, the same way the system stores all its times.
 */
public final class Days {

    private Days() { }

    /** The first moment of the day. */
    public static Instant startOf(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    /** The first moment of the day after: use it as "before" to include the whole of the given day. */
    public static Instant startOfNextDay(LocalDate date) {
        return date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
