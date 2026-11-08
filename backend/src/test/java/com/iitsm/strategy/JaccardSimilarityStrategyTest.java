package com.iitsm.strategy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JaccardSimilarityStrategyTest {

    private static final double DELTA = 1e-9;
    private final JaccardSimilarityStrategy similarity = new JaccardSimilarityStrategy();

    @Test
    void identicalTextScoresOne() {
        assertEquals(1.0, similarity.similarity("payment application down", "payment application down"), DELTA);
    }

    @Test
    void textWithNoSharedKeywordsScoresZero() {
        assertEquals(0.0, similarity.similarity("payment failed", "printer jammed"), DELTA);
    }

    @Test
    void partialOverlapIsSharedOverUnion() {
        // shared {payment}, union {payment, application, unavailable, transaction, failing} -> 1/5
        assertEquals(0.2, similarity.similarity("Payment application unavailable", "Payment transaction failing"), DELTA);
        // shared {database, connection}, union {database, connection, timeout, refused} -> 2/4
        assertEquals(0.5, similarity.similarity("database connection timeout", "database connection refused"), DELTA);
    }

    @Test
    void caseAndPunctuationAndFillerWordsDoNotMatter() {
        assertEquals(1.0, similarity.similarity("The payment app is DOWN!", "payment app down"), DELTA);
    }

    @Test
    void theOrderOfTheArgumentsDoesNotMatter() {
        String a = "vpn connection drops every hour";
        String b = "vpn drops";
        assertEquals(similarity.similarity(a, b), similarity.similarity(b, a), DELTA);
    }

    @Test
    void emptyOrMissingTextScoresZeroInsteadOfFailing() {
        assertEquals(0.0, similarity.similarity("", ""), DELTA);
        assertEquals(0.0, similarity.similarity(null, null), DELTA);
        assertEquals(0.0, similarity.similarity("payment failed", ""), DELTA);
        assertEquals(0.0, similarity.similarity(null, "payment failed"), DELTA);
        assertEquals(0.0, similarity.similarity("the and of", "is not it"), DELTA);   // only filler words
    }

    @Test
    void repeatedWordsDoNotInflateTheScore() {
        // keyword sets are {payment, failed} and {payment}: 1/2, not affected by repetition
        assertEquals(0.5, similarity.similarity("payment payment payment failed", "payment"), DELTA);
    }
}
