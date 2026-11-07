package com.iitsm.strategy;

import com.iitsm.domain.incident.Priority;
import com.iitsm.exception.InvalidInputException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class ScoreBasedPriorityCalculatorTest {

    private static final int NO_BUMP = 1_000_000;   // threshold nobody reaches
    private final ScoreBasedPriorityCalculator calculator = new ScoreBasedPriorityCalculator();

    // Not every score from 1 to 45 can be produced by valid inputs (for example 11 and 26 cannot),
    // so these rows are the closest reachable scores on each side of the tier boundaries.
    @ParameterizedTest(name = "{0}x{1}x{2} = {3} -> {4}")
    @CsvSource({
            "1, 1, 1,  1, P4",
            "1, 1, 4,  4, P4",
            "2, 2, 1,  4, P4",
            "1, 1, 5,  5, P3",
            "3, 3, 1,  9, P3",
            "1, 2, 5, 10, P3",
            "2, 2, 3, 12, P2",
            "3, 2, 4, 24, P2",
            "3, 3, 3, 27, P1",
            "3, 3, 5, 45, P1"
    })
    void scoreMapsToTheExpectedTier(int impact, int urgency, int criticality, int expectedScore, Priority expected) {
        PriorityResult r = calculator.calculate(impact, urgency, criticality, 1, NO_BUMP);
        assertEquals(expectedScore, r.score());
        assertEquals(expected, r.priority());
    }

    @Test
    void higherScoresNeverGiveALessUrgentTier() {
        // Check every valid input combination: sorted by score, the tier must never get less urgent.
        Priority[] bestTierAtScore = new Priority[46];
        for (int i = 1; i <= 3; i++)
            for (int u = 1; u <= 3; u++)
                for (int c = 1; c <= 5; c++) {
                    PriorityResult r = calculator.calculate(i, u, c, 1, NO_BUMP);
                    bestTierAtScore[r.score()] = r.priority();
                }
        Priority previous = null;
        for (Priority p : bestTierAtScore) {
            if (p == null) continue;
            if (previous != null) {
                assertTrue(p.ordinal() <= previous.ordinal(), "tier got less urgent as the score rose");
            }
            previous = p;
        }
    }

    @Test
    void manyAffectedUsersBumpsOneTier() {
        // 2 x 2 x 2 = 8 is P3
        assertEquals(Priority.P3, calculator.calculate(2, 2, 2, 99, 100).priority());
        assertEquals(Priority.P2, calculator.calculate(2, 2, 2, 100, 100).priority());
        assertEquals(Priority.P2, calculator.calculate(2, 2, 2, 5000, 100).priority());
    }

    @Test
    void bumpDoesNotChangeTheScore() {
        PriorityResult r = calculator.calculate(2, 2, 2, 100, 100);
        assertEquals(8, r.score());
    }

    @Test
    void p4BumpsToP3AndP1StaysP1() {
        assertEquals(Priority.P3, calculator.calculate(1, 1, 1, 100, 100).priority());
        assertEquals(Priority.P1, calculator.calculate(3, 3, 5, 100, 100).priority());
    }

    @Test
    void outOfRangeInputsAreRejected() {
        assertThrows(InvalidInputException.class, () -> calculator.calculate(0, 1, 1, 1, 100));
        assertThrows(InvalidInputException.class, () -> calculator.calculate(4, 1, 1, 1, 100));
        assertThrows(InvalidInputException.class, () -> calculator.calculate(1, 0, 1, 1, 100));
        assertThrows(InvalidInputException.class, () -> calculator.calculate(1, 4, 1, 1, 100));
        assertThrows(InvalidInputException.class, () -> calculator.calculate(1, 1, 0, 1, 100));
        assertThrows(InvalidInputException.class, () -> calculator.calculate(1, 1, 6, 1, 100));
        assertThrows(InvalidInputException.class, () -> calculator.calculate(1, 1, 1, 0, 100));
        assertThrows(InvalidInputException.class, () -> calculator.calculate(1, 1, 1, 1, 0));
    }
}
