package com.iitsm.strategy;

import com.iitsm.util.TextTokenizer;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Jaccard similarity of the two texts' keyword sets: shared keywords divided by all distinct keywords.
 * For example "payment application unavailable" and "payment transaction failing" share 1 of 5 keywords, so 0.2.
 */
@Component
public class JaccardSimilarityStrategy implements SimilarityStrategy {

    @Override
    public double similarity(String a, String b) {
        Set<String> first = TextTokenizer.tokens(a);
        Set<String> second = TextTokenizer.tokens(b);

        Set<String> union = new HashSet<>(first);
        union.addAll(second);
        if (union.isEmpty()) {
            return 0.0;
        }
        Set<String> shared = new HashSet<>(first);
        shared.retainAll(second);
        return (double) shared.size() / union.size();
    }
}
