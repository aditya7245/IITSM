package com.iitsm.strategy;

/**
 * Measures how alike two pieces of text are. Kept behind an interface (Strategy pattern) so the
 * duplicate detector can use a smarter measure later without any other code changing.
 */
public interface SimilarityStrategy {

    /** @return a score from 0.0 (nothing in common) to 1.0 (same keywords); the order of the arguments does not matter */
    double similarity(String a, String b);
}
