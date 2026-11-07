package com.iitsm.domain.sla;

import com.iitsm.domain.incident.SlaStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static com.iitsm.domain.incident.SlaStatus.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SlaEvaluatorTest {

    private static final Instant T0 = Instant.parse("2026-11-10T09:00:00Z");
    private static final double THRESHOLD = 0.8;

    private final SlaEvaluator evaluator = new SlaEvaluator();

    // P1: respond within 5 minutes (300 s), resolve within 30 minutes (1800 s), clock running at normal speed.
    private final Deadlines p1 = evaluator.computeDeadlines(T0, 5, 30, 1.0, 0);

    private static Instant at(long secondsAfterStart) {
        return T0.plusSeconds(secondsAfterStart);
    }

    private SlaStatus status(Deadlines d, Instant responded, Instant resolved, Instant pausedAt, long pausedSeconds, long nowSec) {
        return evaluator.evaluate(T0, d.responseDeadline(), d.resolutionDeadline(), responded, resolved,
                pausedAt, pausedSeconds, at(nowSec), THRESHOLD);
    }

    /** Nothing has happened yet; only the response phase is running. */
    private SlaStatus waiting(long nowSec) {
        return status(p1, null, null, null, 0, nowSec);
    }

    // ---------- deadlines ----------

    @Test
    void deadlinesAreStartPlusTheSlaMinutes() {
        assertEquals(at(300), p1.responseDeadline());
        assertEquals(at(1800), p1.resolutionDeadline());
    }

    @Test
    void timeScaleShrinksTheWindows() {
        Deadlines d = evaluator.computeDeadlines(T0, 5, 30, 10.0, 0);
        assertEquals(at(30), d.responseDeadline());
        assertEquals(at(180), d.resolutionDeadline());
    }

    @Test
    void pausedSecondsPushBothDeadlinesLater() {
        Deadlines d = evaluator.computeDeadlines(T0, 5, 30, 1.0, 120);
        assertEquals(at(420), d.responseDeadline());
        assertEquals(at(1920), d.resolutionDeadline());
    }

    // ---------- response phase ----------

    @Test
    void justUnder80PercentIsOnTrackAndExactly80PercentIsAtRisk() {
        assertEquals(ON_TRACK, waiting(0));
        assertEquals(ON_TRACK, waiting(239));   // 80% of 300 s is 240 s
        assertEquals(AT_RISK, waiting(240));
    }

    @Test
    void exactlyAtTheDeadlineIsNotYetViolatedButOneSecondLateIs() {
        assertEquals(AT_RISK, waiting(300));
        assertEquals(VIOLATED, waiting(301));
    }

    @Test
    void respondingLateIsAViolationEvenIfResolvedQuickly() {
        assertEquals(VIOLATED, status(p1, at(301), at(400), null, 0, 5000));
    }

    // ---------- resolution phase ----------

    @Test
    void responseMetInTimeWhileResolutionIsStillRunning() {
        Instant responded = at(60);
        assertEquals(ON_TRACK, status(p1, responded, null, null, 0, 600));    // 600 / 1800 = 33%
        assertEquals(ON_TRACK, status(p1, responded, null, null, 0, 1439));
        assertEquals(AT_RISK, status(p1, responded, null, null, 0, 1440));    // 80% of 1800 s
        assertEquals(AT_RISK, status(p1, responded, null, null, 0, 1800));
        assertEquals(VIOLATED, status(p1, responded, null, null, 0, 1801));
    }

    @Test
    void resolvedInTimeStaysOnTrackNoMatterHowLongAgo() {
        assertEquals(ON_TRACK, status(p1, at(60), at(1700), null, 0, 100_000));
    }

    @Test
    void resolvedLateIsAViolation() {
        assertEquals(VIOLATED, status(p1, at(60), at(1801), null, 0, 100_000));
    }

    // ---------- pausing ----------

    @Test
    void aPausedIncidentDoesNotDriftTowardsItsDeadline() {
        // Responded at 60 s, then paused at 600 s. Hours later it is still ON_TRACK because the clock stopped.
        Instant pausedAt = at(600);
        assertEquals(ON_TRACK, status(p1, at(60), null, pausedAt, 0, 5000));
        assertEquals(ON_TRACK, status(p1, at(60), null, pausedAt, 0, 500_000));
    }

    @Test
    void afterResumingOnlyRunningTimeCounts() {
        // Paused from 600 s to 5000 s = 4400 s. Deadlines move later by that amount.
        Deadlines shifted = evaluator.computeDeadlines(T0, 5, 30, 1.0, 4400);
        assertEquals(at(6200), shifted.resolutionDeadline());

        Instant responded = at(60);
        assertEquals(ON_TRACK, status(shifted, responded, null, null, 4400, 5000));       // 600 s of running time
        assertEquals(AT_RISK, status(shifted, responded, null, null, 4400, 4400 + 1440)); // 80% of the running window
        assertEquals(VIOLATED, status(shifted, responded, null, null, 4400, 6201));
    }

    // ---------- time scale and edge cases ----------

    @Test
    void aTimeScaleOf10WorksEndToEnd() {
        Deadlines fast = evaluator.computeDeadlines(T0, 5, 30, 10.0, 0);   // 30 s and 180 s
        assertEquals(ON_TRACK, status(fast, null, null, null, 0, 23));
        assertEquals(AT_RISK, status(fast, null, null, null, 0, 24));      // 80% of 30 s
        assertEquals(VIOLATED, status(fast, null, null, null, 0, 31));
    }

    @Test
    void anIncidentWithoutDeadlinesIsOnTrack() {
        assertEquals(ON_TRACK, evaluator.evaluate(T0, null, null, null, null, null, 0, at(100_000), THRESHOLD));
    }
}
