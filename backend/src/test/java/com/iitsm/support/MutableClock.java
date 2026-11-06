package com.iitsm.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A clock whose time tests control. Use it to check SLA and escalation behaviour without waiting.
 * Thread-safe, because scheduled jobs may read it while a test advances it.
 * Note: {@link #withZone(ZoneId)} returns an independent copy that no longer follows this clock.
 */
public class MutableClock extends Clock {

    private final Instant initial;
    private final ZoneId zone;
    private final AtomicReference<Instant> now;

    public MutableClock(Instant initial) {
        this(initial, ZoneOffset.UTC);
    }

    public MutableClock(Instant initial, ZoneId zone) {
        this.initial = initial;
        this.zone = zone;
        this.now = new AtomicReference<>(initial);
    }

    @Override
    public Instant instant() {
        return now.get();
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(now.get(), zone);
    }

    public void set(Instant instant) {
        now.set(instant);
    }

    public void advance(Duration duration) {
        now.updateAndGet(t -> t.plus(duration));
    }

    /** Back to the starting time; call before each test that depends on time. */
    public void reset() {
        now.set(initial);
    }
}
