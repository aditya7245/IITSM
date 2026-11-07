package com.iitsm.domain.sla;

import com.iitsm.domain.incident.SlaStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * The SLA stopwatch logic, with no database and no clock of its own: every time is passed in.
 * <p>
 * An incident has two phases, each with a deadline: responding (until a technician acknowledges)
 * and resolving (until it is resolved). While the clock is paused (waiting for the reporter) time does not count.
 */
@Component
public class SlaEvaluator {

    /**
     * Deadline = start + window + time already spent paused.
     * The window is the SLA minutes divided by the time scale, so a time scale of 10 makes a 30-minute SLA last 3 minutes.
     */
    public Deadlines computeDeadlines(Instant start, int responseMinutes, int resolutionMinutes,
                                      double timeScale, long pausedSeconds) {
        Duration paused = Duration.ofSeconds(pausedSeconds);
        return new Deadlines(
                start.plus(window(responseMinutes, timeScale)).plus(paused),
                start.plus(window(resolutionMinutes, timeScale)).plus(paused));
    }

    /**
     * @param respondedAt  when the incident was acknowledged, or null if not yet
     * @param resolvedAt   when the incident was resolved, or null if not yet
     * @param slaPausedAt  when the clock was paused, or null if it is running
     * @param pausedSeconds total seconds the clock has been paused before now (already included in the deadlines)
     * @param warningThreshold fraction of a phase after which it is AT_RISK (0.8 = 80%)
     * @return the worse of the response phase and the resolution phase
     */
    public SlaStatus evaluate(Instant createdAt, Instant responseDeadline, Instant resolutionDeadline,
                              Instant respondedAt, Instant resolvedAt, Instant slaPausedAt, long pausedSeconds,
                              Instant now, double warningThreshold) {
        Instant effectiveNow = slaPausedAt != null ? slaPausedAt : now;
        SlaStatus response = phaseStatus(createdAt, responseDeadline, respondedAt, effectiveNow, pausedSeconds, warningThreshold);
        SlaStatus resolution = phaseStatus(createdAt, resolutionDeadline, resolvedAt, effectiveNow, pausedSeconds, warningThreshold);
        return response.isMoreSevereThan(resolution) ? response : resolution;
    }

    private SlaStatus phaseStatus(Instant createdAt, Instant deadline, Instant completedAt, Instant effectiveNow,
                                  long pausedSeconds, double warningThreshold) {
        if (deadline == null) {
            return SlaStatus.ON_TRACK;   // not classified yet, so no SLA applies
        }
        boolean completed = completedAt != null;
        Instant reference = completed ? completedAt : effectiveNow;

        if (reference.isAfter(deadline)) {
            return SlaStatus.VIOLATED;
        }
        if (completed) {
            return SlaStatus.ON_TRACK;   // finished in time
        }
        Duration paused = Duration.ofSeconds(pausedSeconds);
        long elapsedMillis = Duration.between(createdAt, reference).minus(paused).toMillis();
        long windowMillis = Duration.between(createdAt, deadline).minus(paused).toMillis();
        return elapsedMillis >= warningThreshold * windowMillis ? SlaStatus.AT_RISK : SlaStatus.ON_TRACK;
    }

    private static Duration window(int minutes, double timeScale) {
        return Duration.ofMillis(Math.round(minutes * 60_000.0 / timeScale));
    }
}
