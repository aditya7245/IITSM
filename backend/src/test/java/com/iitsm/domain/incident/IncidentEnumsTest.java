package com.iitsm.domain.incident;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class IncidentEnumsTest {

    @Test
    void bumpMovesOneTierMoreUrgentAndP1Stays() {
        assertEquals(Priority.P3, Priority.P4.bump());
        assertEquals(Priority.P2, Priority.P3.bump());
        assertEquals(Priority.P1, Priority.P2.bump());
        assertEquals(Priority.P1, Priority.P1.bump());
    }

    @Test
    void terminalStatusesAreExactlyClosedCancelledDuplicate() {
        Set<IncidentStatus> expected = EnumSet.of(IncidentStatus.CLOSED, IncidentStatus.CANCELLED, IncidentStatus.DUPLICATE);
        for (IncidentStatus s : IncidentStatus.values()) {
            assertEquals(expected.contains(s), s.isTerminal(), s.name());
            assertEquals(!expected.contains(s), s.isOpen(), s.name());
        }
        assertEquals(expected, IncidentStatus.terminalStatuses());
    }

    @Test
    void workloadStatusesAreExactlyAssignedAcknowledgedInProgressPendingInfo() {
        Set<IncidentStatus> expected = EnumSet.of(IncidentStatus.ASSIGNED, IncidentStatus.ACKNOWLEDGED,
                IncidentStatus.IN_PROGRESS, IncidentStatus.PENDING_INFO);
        for (IncidentStatus s : IncidentStatus.values()) {
            assertEquals(expected.contains(s), s.countsTowardWorkload(), s.name());
        }
        assertEquals(expected, IncidentStatus.workloadStatuses());
    }

    @Test
    void resolvedAndVerifiedAreOpenButNoLongerCountTowardWorkload() {
        assertTrue(IncidentStatus.RESOLVED.isOpen());
        assertTrue(IncidentStatus.VERIFIED.isOpen());
        assertFalse(IncidentStatus.RESOLVED.countsTowardWorkload());
        assertFalse(IncidentStatus.VERIFIED.countsTowardWorkload());
    }

    @Test
    void slaStatusIsOrderedBySeverity() {
        assertTrue(SlaStatus.AT_RISK.isMoreSevereThan(SlaStatus.ON_TRACK));
        assertTrue(SlaStatus.VIOLATED.isMoreSevereThan(SlaStatus.AT_RISK));
        assertFalse(SlaStatus.ON_TRACK.isMoreSevereThan(SlaStatus.AT_RISK));
        assertFalse(SlaStatus.VIOLATED.isMoreSevereThan(SlaStatus.VIOLATED));
    }

    @Test
    void commentTypesAreTheThreeFromTheSchema() {
        assertEquals(3, CommentType.values().length);
        assertNotNull(CommentType.valueOf("NOTE"));
        assertNotNull(CommentType.valueOf("INFO_REQUEST"));
        assertNotNull(CommentType.valueOf("INFO_RESPONSE"));
    }
}
