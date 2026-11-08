package com.iitsm.util;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Turns free text into a set of clean lowercase keywords. The category suggester and the duplicate detector
 * both compare incidents by their keywords, so they share this one definition of "a word".
 * <p>
 * Rules: lowercase; split on anything that is not a letter or digit; drop words shorter than 2 characters;
 * drop common filler words (stop words); no stemming, so "payments" and "payment" are different keywords.
 */
public final class TextTokenizer {

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "is", "are", "was", "were", "be", "been", "to", "of", "in", "on", "at", "for",
            "and", "or", "with", "my", "our", "we", "i", "it", "its", "this", "that", "has", "have", "had",
            "not", "no", "from", "by", "as", "can", "cannot", "please");

    private TextTokenizer() { }

    /** @return the distinct keywords of the text, in order of first appearance; empty for null or blank text */
    public static Set<String> tokens(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String word : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (word.length() >= 2 && !STOP_WORDS.contains(word)) {
                result.add(word);
            }
        }
        return Collections.unmodifiableSet(result);
    }
}
