package com.iitsm.domain.incident;

import com.iitsm.domain.user.Technician;
import com.iitsm.exception.InvalidInputException;
import com.iitsm.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.iitsm.domain.incident.IncidentStatus.*;
import static org.junit.jupiter.api.Assertions.*;

/** Plain unit tests: the Incident methods need no database. */
class IncidentBehaviourTest {

    private static final Instant T0 = Instant.parse("2026-11-10T09:00:00Z");

    private static Instant at(long secondsAfterStart) {
        return T0.plusSeconds(secondsAfterStart);
    }

    private static Technician technician(String name) {
        return new Technician(name + "@iitsm.local", "hash", name, null, 5, 3);
    }

    private static Incident newIncident() {
        return new Incident("INC1", "Payment down", "Cannot pay", null, null, null, (short) 3, (short) 3, 10, T0);
    }

    private static Incident classified() {
        Incident i = newIncident();
        i.classify(Priority.P1, 27, at(300), at(1800));
        return i;
    }

    private static Incident assigned() {
        Incident i = classified();
        i.assignTo(technician("tech"));
        return i;
    }

    private static Incident acknowledged() {
        Incident i = assigned();
        i.acknowledge(at(60));
        return i;
    }

    private static Incident inProgress() {
        Incident i = acknowledged();
        i.startProgress();
        return i;
    }

    private static Incident resolved() {
        Incident i = inProgress();
        i.resolve("Restarted the gateway", at(900));
        return i;
    }

    // ---------- happy path ----------

    @Test
    void fullLifecycleWalksThroughEveryStatusAndRecordsTheDetails() {
        Incident i = newIncident();
        assertEquals(NEW, i.getStatus());

        i.classify(Priority.P1, 27, at(300), at(1800));
        assertEquals(CLASSIFIED, i.getStatus());
        assertEquals(Priority.P1, i.getPriority());
        assertEquals(27, i.getPriorityScore());
        assertEquals(at(300), i.getResponseDeadline());
        assertEquals(at(1800), i.getResolutionDeadline());

        Technician tech = technician("tech");
        i.assignTo(tech);
        assertEquals(ASSIGNED, i.getStatus());
        assertSame(tech, i.getAssignee());

        i.acknowledge(at(60));
        assertEquals(ACKNOWLEDGED, i.getStatus());
        assertEquals(at(60), i.getRespondedAt());

        i.startProgress();
        assertEquals(IN_PROGRESS, i.getStatus());

        i.resolve("Restarted the gateway", at(900));
        assertEquals(RESOLVED, i.getStatus());
        assertEquals("Restarted the gateway", i.getResolution());
        assertEquals(at(900), i.getResolvedAt());

        i.verify();
        assertEquals(VERIFIED, i.getStatus());

        i.close(at(1000));
        assertEquals(CLOSED, i.getStatus());
        assertEquals(at(1000), i.getClosedAt());
    }

    // ---------- pausing the SLA clock ----------

    @Test
    void waitingForInfoPausesTheClockAndResumingMovesTheDeadlinesLater() {
        Incident i = inProgress();

        i.requestInfo(at(600));
        assertEquals(PENDING_INFO, i.getStatus());
        assertEquals(at(600), i.getSlaPausedAt());

        i.receiveInfo(at(1000));
        assertEquals(IN_PROGRESS, i.getStatus());
        assertNull(i.getSlaPausedAt());
        assertEquals(400, i.getSlaPausedSeconds());
        assertEquals(at(700), i.getResponseDeadline());
        assertEquals(at(2200), i.getResolutionDeadline());
    }

    @Test
    void severalPausesAddUp() {
        Incident i = inProgress();
        i.requestInfo(at(600));
        i.receiveInfo(at(1000));    // 400 s
        i.requestInfo(at(1100));
        i.receiveInfo(at(1160));    // 60 s

        assertEquals(460, i.getSlaPausedSeconds());
        assertEquals(at(2260), i.getResolutionDeadline());
    }

    // ---------- reopening ----------

    @Test
    void reopeningAResolvedIncidentClearsTheResolution() {
        Incident i = resolved();
        i.reopen();
        assertEquals(IN_PROGRESS, i.getStatus());
        assertNull(i.getResolution());
        assertNull(i.getResolvedAt());
    }

    // ---------- assignment ----------

    @Test
    void reassigningWhileAssignedKeepsTheStatusAndChangesTheTechnician() {
        Incident i = assigned();
        Technician other = technician("other");
        i.assignTo(other);
        assertEquals(ASSIGNED, i.getStatus());
        assertSame(other, i.getAssignee());
    }

    @Test
    void reassigningAfterAcknowledgementIsRejected() {
        Incident i = acknowledged();
        assertThrows(InvalidStateTransitionException.class, () -> i.assignTo(technician("other")));
    }

    @Test
    void assigningNobodyIsRejected() {
        assertThrows(InvalidInputException.class, () -> classified().assignTo(null));
    }

    // ---------- cancel and duplicate ----------

    @Test
    void cancelWorksBeforeWorkStartsButNotAfter() {
        Incident n = newIncident();
        n.cancel();
        assertEquals(CANCELLED, n.getStatus());

        Incident c = classified();
        c.cancel();
        assertEquals(CANCELLED, c.getStatus());

        Incident a = assigned();
        a.cancel();
        assertEquals(CANCELLED, a.getStatus());

        assertThrows(InvalidStateTransitionException.class, () -> acknowledged().cancel());
    }

    @Test
    void markDuplicateLinksToTheOriginal() {
        Incident original = newIncident();
        Incident copy = assigned();
        copy.markDuplicateOf(original);
        assertEquals(DUPLICATE, copy.getStatus());
        assertSame(original, copy.getDuplicateOf());
    }

    @Test
    void markDuplicateRejectsNullSelfAndLateStatuses() {
        Incident i = newIncident();
        assertThrows(InvalidInputException.class, () -> i.markDuplicateOf(null));
        assertThrows(InvalidInputException.class, () -> i.markDuplicateOf(i));
        assertEquals(NEW, i.getStatus());   // a rejected call changes nothing
        assertThrows(InvalidStateTransitionException.class, () -> inProgress().markDuplicateOf(newIncident()));
    }

    // ---------- priority ----------

    @Test
    void priorityCanBeChangedBetweenClassificationAndResolution() {
        Incident i = inProgress();
        i.changePriority(Priority.P3, 8, at(3600), at(28800));
        assertEquals(Priority.P3, i.getPriority());
        assertEquals(8, i.getPriorityScore());
        assertEquals(at(3600), i.getResponseDeadline());
        assertEquals(at(28800), i.getResolutionDeadline());
    }

    @Test
    void priorityCannotBeChangedBeforeClassificationOrAfterResolution() {
        assertThrows(InvalidInputException.class,
                () -> newIncident().changePriority(Priority.P2, 12, at(1), at(2)));
        assertThrows(InvalidInputException.class,
                () -> resolved().changePriority(Priority.P2, 12, at(1), at(2)));
    }

    // ---------- SLA status and escalation ----------

    @Test
    void slaStatusOnlyGoesUp() {
        Incident i = newIncident();
        assertTrue(i.raiseSlaStatus(SlaStatus.AT_RISK));
        assertEquals(SlaStatus.AT_RISK, i.getSlaStatus());
        assertFalse(i.raiseSlaStatus(SlaStatus.ON_TRACK));
        assertFalse(i.raiseSlaStatus(SlaStatus.AT_RISK));
        assertTrue(i.raiseSlaStatus(SlaStatus.VIOLATED));
        assertFalse(i.raiseSlaStatus(SlaStatus.AT_RISK));
        assertEquals(SlaStatus.VIOLATED, i.getSlaStatus());
    }

    @Test
    void escalationLevelCountsUp() {
        Incident i = newIncident();
        i.incrementEscalationLevel();
        i.incrementEscalationLevel();
        assertEquals(2, i.getEscalationLevel());
    }

    // ---------- invalid calls: one per method, and failures change nothing ----------

    @Test
    void everyMethodRejectsBeingCalledInTheWrongStatus() {
        Incident fresh = newIncident();   // NEW
        assertThrows(InvalidStateTransitionException.class, () -> fresh.assignTo(technician("t")));
        assertThrows(InvalidStateTransitionException.class, () -> fresh.acknowledge(at(1)));
        assertThrows(InvalidStateTransitionException.class, fresh::startProgress);
        assertThrows(InvalidStateTransitionException.class, () -> fresh.requestInfo(at(1)));
        assertThrows(InvalidStateTransitionException.class, () -> fresh.receiveInfo(at(1)));
        assertThrows(InvalidStateTransitionException.class, () -> fresh.resolve("x", at(1)));
        assertThrows(InvalidStateTransitionException.class, fresh::verify);
        assertThrows(InvalidStateTransitionException.class, fresh::reopen);
        assertThrows(InvalidStateTransitionException.class, () -> fresh.close(at(1)));
        assertEquals(NEW, fresh.getStatus());

        Incident c = classified();
        assertThrows(InvalidStateTransitionException.class, () -> c.classify(Priority.P2, 12, at(1), at(2)));
    }

    @Test
    void methodsWhoseTargetIsReachableFromElsewhereStillCheckTheSourceStatus() {
        // IN_PROGRESS can be entered from ACKNOWLEDGED, PENDING_INFO and RESOLVED, but each method has one valid source.
        assertThrows(InvalidStateTransitionException.class, () -> assigned().startProgress());
        Incident pending = inProgress();
        pending.requestInfo(at(100));
        assertThrows(InvalidStateTransitionException.class, pending::startProgress);
        assertThrows(InvalidStateTransitionException.class, pending::reopen);
        assertThrows(InvalidStateTransitionException.class, () -> acknowledged().receiveInfo(at(100)));
        assertThrows(InvalidStateTransitionException.class, () -> acknowledged().reopen());
    }

    @Test
    void invalidInputLeavesTheIncidentUnchanged() {
        Incident i = inProgress();
        assertThrows(InvalidInputException.class, () -> i.resolve(" ", at(900)));
        assertThrows(InvalidInputException.class, () -> i.resolve(null, at(900)));
        assertEquals(IN_PROGRESS, i.getStatus());
        assertNull(i.getResolution());
        assertNull(i.getResolvedAt());

        Incident n = newIncident();
        assertThrows(InvalidInputException.class, () -> n.classify(null, 1, at(1), at(2)));
        assertThrows(InvalidInputException.class, () -> n.classify(Priority.P1, 1, null, at(2)));
        assertEquals(NEW, n.getStatus());
        assertNull(n.getPriority());
    }
}
