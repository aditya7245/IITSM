package com.iitsm.security;

import com.iitsm.exception.TooManyRequestsException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Counts events per key (an e-mail address, a user id) in a sliding time window and refuses once a limit is
 * reached. In memory and per server, which is enough to stop password guessing and runaway use of the paid AI
 * provider on a single instance; several instances would need a shared store such as Redis.
 */
public class RateLimiter {

    private final int limit;
    private final Duration window;
    private final Clock clock;
    private final String what;
    private final Map<String, Deque<Instant>> events = new ConcurrentHashMap<>();

    /**
     * @param limit  how many events are allowed per key inside the window
     * @param window the length of the sliding window
     * @param what   what is being limited, for the error message (for example "failed logins")
     */
    public RateLimiter(int limit, Duration window, Clock clock, String what) {
        if (limit < 1 || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("A rate limit needs a positive limit and window");
        }
        this.limit = limit;
        this.window = window;
        this.clock = clock;
        this.what = what;
    }

    /** @throws TooManyRequestsException if the key already used up its limit in the current window */
    public void check(String key) {
        Deque<Instant> recent = events.get(normalise(key));
        if (recent == null) {
            return;
        }
        synchronized (recent) {
            Instant now = clock.instant();
            prune(recent, now);
            if (recent.size() >= limit) {
                Duration wait = Duration.between(now, recent.peekFirst().plus(window));
                long minutes = Math.max(1, (wait.toSeconds() + 59) / 60);
                throw new TooManyRequestsException("Too many " + what + ". Try again in " + minutes
                        + (minutes == 1 ? " minute." : " minutes."));
            }
        }
    }

    /** Records one event for the key. */
    public void record(String key) {
        Deque<Instant> recent = events.computeIfAbsent(normalise(key), k -> new ArrayDeque<>());
        synchronized (recent) {
            Instant now = clock.instant();
            prune(recent, now);
            recent.addLast(now);
        }
    }

    /** Checks and then records in one step: for limits on every request rather than only on failures. */
    public void acquire(String key) {
        Deque<Instant> recent = events.computeIfAbsent(normalise(key), k -> new ArrayDeque<>());
        synchronized (recent) {
            check(key);
            recent.addLast(clock.instant());
        }
    }

    /** Forgets the key's history, for example after a successful login. */
    public void reset(String key) {
        events.remove(normalise(key));
    }

    private void prune(Deque<Instant> recent, Instant now) {
        Instant cutoff = now.minus(window);
        while (!recent.isEmpty() && !recent.peekFirst().isAfter(cutoff)) {
            recent.pollFirst();
        }
    }

    private static String normalise(String key) {
        return key == null ? "" : key.strip().toLowerCase(Locale.ROOT);
    }
}
