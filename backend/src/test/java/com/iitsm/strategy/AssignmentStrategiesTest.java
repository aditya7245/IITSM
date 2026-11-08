package com.iitsm.strategy;

import com.iitsm.domain.user.Technician;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Plain unit tests for the three assignment strategies; technicians are built in memory with chosen ids. */
class AssignmentStrategiesTest {

    private final SkillBasedStrategy skillBased = new SkillBasedStrategy();
    private final LoadBasedStrategy loadBased = new LoadBasedStrategy();

    private static Technician tech(long id, int experienceYears) {
        Technician t = new Technician("t" + id + "@iitsm.local", "hash", "Tech " + id, null, 5, experienceYears);
        try {
            Field f = com.iitsm.domain.common.BaseEntity.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(t, id);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        return t;
    }

    private static TechnicianCandidate candidate(long id, int experience, int active, int proficiency) {
        return new TechnicianCandidate(tech(id, experience), active, proficiency);
    }

    private static List<Long> ids(List<TechnicianCandidate> ranked) {
        List<Long> ids = new ArrayList<>();
        for (TechnicianCandidate c : ranked) {
            ids.add(c.technician().getId());
        }
        return ids;
    }

    // ---------- skill based ----------

    @Test
    void skillBasedPutsTheMostSkilledFirst() {
        List<TechnicianCandidate> ranked = skillBased.rank(List.of(
                candidate(1, 5, 0, 3), candidate(2, 5, 0, 5), candidate(3, 5, 0, 1)), true);
        assertEquals(List.of(2L, 1L, 3L), ids(ranked));
    }

    @Test
    void skillBasedBreaksTiesByLoadThenExperienceThenId() {
        // same proficiency: fewer active incidents wins
        assertEquals(List.of(2L, 1L), ids(skillBased.rank(List.of(
                candidate(1, 5, 3, 4), candidate(2, 5, 1, 4)), true)));
        // same proficiency and load: more experience wins
        assertEquals(List.of(2L, 1L), ids(skillBased.rank(List.of(
                candidate(1, 2, 1, 4), candidate(2, 9, 1, 4)), true)));
        // everything equal: lower id wins
        assertEquals(List.of(1L, 2L, 3L), ids(skillBased.rank(List.of(
                candidate(3, 5, 1, 4), candidate(1, 5, 1, 4), candidate(2, 5, 1, 4)), true)));
    }

    @Test
    void skillBasedDropsTechniciansWithoutTheRequiredSkill() {
        List<TechnicianCandidate> ranked = skillBased.rank(List.of(
                candidate(1, 5, 0, 0), candidate(2, 5, 0, 3), candidate(3, 5, 0, 0)), true);
        assertEquals(List.of(2L), ids(ranked));
    }

    @Test
    void skillBasedReturnsNobodyWhenNoOneHasTheSkill() {
        assertTrue(skillBased.rank(List.of(candidate(1, 5, 0, 0), candidate(2, 5, 0, 0)), true).isEmpty());
    }

    @Test
    void skillBasedKeepsEveryoneWhenNoSkillIsRequired() {
        List<TechnicianCandidate> ranked = skillBased.rank(List.of(
                candidate(1, 5, 2, 0), candidate(2, 5, 1, 0)), false);
        assertEquals(List.of(2L, 1L), ids(ranked));   // all proficiency 0, so the lighter load wins
    }

    @Test
    void emptyInputGivesEmptyOutputForEveryStrategy() {
        assertTrue(skillBased.rank(List.of(), true).isEmpty());
        assertTrue(loadBased.rank(List.of(), false).isEmpty());
        assertTrue(new RoundRobinStrategy().rank(List.of(), false).isEmpty());
    }

    // ---------- load based ----------

    @Test
    void loadBasedPutsTheLeastBusyFirstAndIgnoresSkill() {
        List<TechnicianCandidate> ranked = loadBased.rank(List.of(
                candidate(1, 5, 4, 5), candidate(2, 5, 0, 0), candidate(3, 5, 2, 3)), true);
        assertEquals(List.of(2L, 3L, 1L), ids(ranked));
    }

    @Test
    void loadBasedBreaksTiesByExperienceThenId() {
        assertEquals(List.of(2L, 1L), ids(loadBased.rank(List.of(
                candidate(1, 2, 1, 0), candidate(2, 9, 1, 0)), false)));
        assertEquals(List.of(1L, 2L), ids(loadBased.rank(List.of(
                candidate(2, 5, 1, 0), candidate(1, 5, 1, 0)), false)));
    }

    @Test
    void rankingDoesNotChangeTheInputList() {
        List<TechnicianCandidate> input = new ArrayList<>(List.of(candidate(2, 5, 3, 1), candidate(1, 5, 0, 1)));
        List<TechnicianCandidate> copy = new ArrayList<>(input);
        loadBased.rank(input, false);
        skillBased.rank(input, false);
        assertEquals(copy, input);
    }

    // ---------- round robin ----------

    @Test
    void roundRobinStartsAtTheLowestIdAndIgnoresInputOrder() {
        RoundRobinStrategy rr = new RoundRobinStrategy();
        assertEquals(List.of(1L, 2L, 3L), ids(rr.rank(List.of(
                candidate(3, 5, 0, 0), candidate(1, 5, 0, 0), candidate(2, 5, 0, 0)), false)));
    }

    @Test
    void roundRobinContinuesAfterTheLastAssignedAndWrapsAround() {
        RoundRobinStrategy rr = new RoundRobinStrategy();
        List<TechnicianCandidate> all = List.of(candidate(1, 5, 0, 0), candidate(2, 5, 0, 0), candidate(3, 5, 0, 0));

        rr.recordAssigned(tech(2, 5));
        assertEquals(List.of(3L, 1L, 2L), ids(rr.rank(all, false)));

        rr.recordAssigned(tech(3, 5));
        assertEquals(List.of(1L, 2L, 3L), ids(rr.rank(all, false)));
    }

    @Test
    void roundRobinStillWorksWhenTheLastAssignedTechnicianIsNoLongerEligible() {
        RoundRobinStrategy rr = new RoundRobinStrategy();
        rr.recordAssigned(tech(2, 5));
        List<TechnicianCandidate> withoutTwo = List.of(candidate(1, 5, 0, 0), candidate(3, 5, 0, 0), candidate(4, 5, 0, 0));
        assertEquals(List.of(3L, 4L, 1L), ids(rr.rank(withoutTwo, false)));
    }

    @Test
    void roundRobinTakesTurnsWhenUsedRepeatedly() {
        RoundRobinStrategy rr = new RoundRobinStrategy();
        List<TechnicianCandidate> all = List.of(candidate(1, 5, 0, 0), candidate(2, 5, 0, 0), candidate(3, 5, 0, 0));
        List<Long> chosen = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            TechnicianCandidate next = rr.rank(all, false).get(0);
            rr.recordAssigned(next.technician());
            chosen.add(next.technician().getId());
        }
        assertEquals(List.of(1L, 2L, 3L, 1L, 2L, 3L), chosen);
    }

    @Test
    void strategiesHaveTheNamesStoredInTheSettings() {
        assertEquals("SKILL_BASED", skillBased.name());
        assertEquals("LOAD_BASED", loadBased.name());
        assertEquals("ROUND_ROBIN", new RoundRobinStrategy().name());
    }
}
