package com.iitsm.security;

import com.iitsm.exception.TooManyRequestsException;
import com.iitsm.support.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-15T10:00:00Z"));
    private final RateLimiter limiter = new RateLimiter(3, Duration.ofMinutes(10), clock, "attempts");

    @Test
    void theLimitIsReachedAfterTheAllowedNumberOfEvents() {
        limiter.record("a@x");
        limiter.record("a@x");
        limiter.check("a@x");                     // 2 of 3: still fine
        limiter.record("a@x");
        TooManyRequestsException e = assertThrows(TooManyRequestsException.class, () -> limiter.check("a@x"));
        assertEquals(429, e.getStatus().value());
        assertEquals("Too many attempts. Try again in 10 minutes.", e.getMessage());
    }

    @Test
    void keysAreCaseInsensitiveAndIndependent() {
        for (int i = 0; i < 3; i++) {
            limiter.record("A@X");
        }
        assertThrows(TooManyRequestsException.class, () -> limiter.check("a@x"));
        limiter.check("b@x");
    }

    @Test
    void oldEventsSlideOutOfTheWindow() {
        limiter.record("a@x");
        clock.advance(Duration.ofMinutes(6));
        limiter.record("a@x");
        limiter.record("a@x");
        assertThrows(TooManyRequestsException.class, () -> limiter.check("a@x"));
        clock.advance(Duration.ofMinutes(4));     // the first event is now 10 minutes old
        limiter.check("a@x");
    }

    @Test
    void resetForgetsTheHistoryAndAcquireCountsEveryCall() {
        for (int i = 0; i < 3; i++) {
            limiter.record("a@x");
        }
        limiter.reset("a@x");
        limiter.check("a@x");

        limiter.acquire("b@x");
        limiter.acquire("b@x");
        limiter.acquire("b@x");
        assertThrows(TooManyRequestsException.class, () -> limiter.acquire("b@x"));
    }

    @Test
    void aLimitNeedsPositiveValues() {
        assertThrows(IllegalArgumentException.class, () -> new RateLimiter(0, Duration.ofMinutes(1), clock, "x"));
        assertThrows(IllegalArgumentException.class, () -> new RateLimiter(1, Duration.ZERO, clock, "x"));
    }
}
