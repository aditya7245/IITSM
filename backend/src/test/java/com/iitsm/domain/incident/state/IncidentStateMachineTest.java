package com.iitsm.domain.incident.state;

import com.iitsm.domain.incident.IncidentStatus;
import com.iitsm.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.iitsm.domain.incident.IncidentStatus.*;
import static org.junit.jupiter.api.Assertions.*;

class IncidentStateMachineTest {

    /** The lifecycle rules, written out independently of the state classes. */
    private static final Map<IncidentStatus, Set<IncidentStatus>> EXPECTED = new EnumMap<>(IncidentStatus.class);

    static {
        EXPECTED.put(NEW, EnumSet.of(CLASSIFIED, CANCELLED, DUPLICATE));
        EXPECTED.put(CLASSIFIED, EnumSet.of(ASSIGNED, CANCELLED, DUPLICATE));
        EXPECTED.put(ASSIGNED, EnumSet.of(ACKNOWLEDGED, CANCELLED, DUPLICATE));
        EXPECTED.put(ACKNOWLEDGED, EnumSet.of(IN_PROGRESS, DUPLICATE));
        EXPECTED.put(IN_PROGRESS, EnumSet.of(PENDING_INFO, RESOLVED));
        EXPECTED.put(PENDING_INFO, EnumSet.of(IN_PROGRESS));
        EXPECTED.put(RESOLVED, EnumSet.of(VERIFIED, IN_PROGRESS));
        EXPECTED.put(VERIFIED, EnumSet.of(CLOSED));
        EXPECTED.put(CLOSED, EnumSet.noneOf(IncidentStatus.class));
        EXPECTED.put(CANCELLED, EnumSet.noneOf(IncidentStatus.class));
        EXPECTED.put(DUPLICATE, EnumSet.noneOf(IncidentStatus.class));
    }

    @Test
    void everyOneOfTheElevenTimesElevenPairsMatchesTheRules() {
        for (IncidentStatus from : IncidentStatus.values()) {
            for (IncidentStatus to : IncidentStatus.values()) {
                boolean expected = EXPECTED.get(from).contains(to);
                assertEquals(expected, IncidentStateMachine.canTransition(from, to), from + " -> " + to);
                if (expected) {
                    assertDoesNotThrow(() -> IncidentStateMachine.requireTransition(from, to), from + " -> " + to);
                } else {
                    assertThrows(InvalidStateTransitionException.class,
                            () -> IncidentStateMachine.requireTransition(from, to), from + " -> " + to);
                }
            }
        }
    }

    @Test
    void newToClosedIsRejectedWithBothStatusesInTheMessage() {
        InvalidStateTransitionException e = assertThrows(InvalidStateTransitionException.class,
                () -> IncidentStateMachine.requireTransition(NEW, CLOSED));
        assertTrue(e.getMessage().contains("NEW"));
        assertTrue(e.getMessage().contains("CLOSED"));
    }

    @Test
    void stayingInTheSameStatusIsNotATransition() {
        for (IncidentStatus s : IncidentStatus.values()) {
            assertFalse(IncidentStateMachine.canTransition(s, s), s.name());
        }
    }

    @Test
    void everyStatusHasAStateObjectThatReportsItsOwnStatus() {
        for (IncidentStatus s : IncidentStatus.values()) {
            assertNotNull(IncidentStates.of(s), s.name());
            assertEquals(s, IncidentStates.of(s).status());
        }
    }

    @Test
    void finalStatesHaveNoWayOutAndAllOthersHaveOne() {
        for (IncidentStatus s : IncidentStatus.values()) {
            Set<IncidentStatus> next = IncidentStates.of(s).allowedNext();
            assertEquals(s.isTerminal(), next.isEmpty(), s.name());
        }
    }

    @Test
    void allowedNextCannotBeModified() {
        Set<IncidentStatus> next = IncidentStates.of(NEW).allowedNext();
        assertThrows(UnsupportedOperationException.class, () -> next.add(CLOSED));
    }

    @Test
    void nullStatusesAreRejected() {
        assertThrows(NullPointerException.class, () -> IncidentStateMachine.canTransition(null, NEW));
        assertThrows(NullPointerException.class, () -> IncidentStateMachine.canTransition(NEW, null));
    }
}
