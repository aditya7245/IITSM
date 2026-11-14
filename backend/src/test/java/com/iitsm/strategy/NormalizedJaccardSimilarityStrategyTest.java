package com.iitsm.strategy;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NormalizedJaccardSimilarityStrategyTest {

    private static final double DELTA = 1e-9;
    private static final double DEFAULT_THRESHOLD = 0.3;
    private final NormalizedJaccardSimilarityStrategy similarity = new NormalizedJaccardSimilarityStrategy();

    @Test
    void theSpecificationsDuplicateExampleIsRecognised() {
        String a = "Payment application unavailable";
        String b = "Payment transaction failing";
        String c = "Unable to make payment";

        assertEquals(0.5, similarity.similarity(a, b), DELTA);       // {payment, outage} of 4 keywords
        assertEquals(2.0 / 3, similarity.similarity(a, c), DELTA);   // "make" is filler: {payment, outage} of 3
        assertEquals(2.0 / 3, similarity.similarity(b, c), DELTA);
        assertTrue(similarity.similarity(b, c) >= DEFAULT_THRESHOLD);
    }

    @Test
    void pluralsAndVerbEndingsBecomeTheSameKeyword() {
        assertEquals(Set.of("payment", "transaction", "outage"),
                NormalizedJaccardSimilarityStrategy.keywords("payments transactions failed"));
        assertEquals("transaction", NormalizedJaccardSimilarityStrategy.stem("transactions"));
        assertEquals("fail", NormalizedJaccardSimilarityStrategy.stem("failing"));
        assertEquals("fail", NormalizedJaccardSimilarityStrategy.stem("failed"));
        assertEquals("query", NormalizedJaccardSimilarityStrategy.stem("queries"));
        assertEquals("access", NormalizedJaccardSimilarityStrategy.stem("access"));   // "ss" is not a plural
        assertEquals("bus", NormalizedJaccardSimilarityStrategy.stem("bus"));         // too short to touch
    }

    @Test
    void synonymsCountAsTheSameProblem() {
        assertEquals(1.0, similarity.similarity("Email app down", "mail application unavailable"), DELTA);
    }

    @Test
    void unrelatedProblemsStayApart() {
        assertEquals(0.0, similarity.similarity("Payment application unavailable", "Printer jammed on floor three"), DELTA);
        assertTrue(similarity.similarity("VPN keeps disconnecting", "Laptop screen flickers") < DEFAULT_THRESHOLD);
    }

    @Test
    void emptyTextHasNoSimilarityAndTheScoreIsSymmetric() {
        assertEquals(0.0, similarity.similarity(null, ""), DELTA);
        assertEquals(0.0, similarity.similarity("make it", "use any"), DELTA);   // only filler words
        assertEquals(similarity.similarity("database timeout", "slow database queries"),
                similarity.similarity("slow database queries", "database timeout"), DELTA);
    }
}
