package com.iitsm.strategy;

import com.iitsm.util.TextTokenizer;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Jaccard similarity after the keywords have been normalised, so that different words for the same problem count
 * as the same keyword. This is the strategy the duplicate detector uses ({@link Primary}); the plain
 * {@link JaccardSimilarityStrategy} stays available and is interchangeable through {@link SimilarityStrategy}.
 * <p>
 * Normalising means:
 * <ol>
 *   <li>light stemming: "transactions" becomes "transaction", "failing" and "failed" become "fail";</li>
 *   <li>synonym groups: "unavailable", "down", "fail", "error", "unable", "crash" ... all mean "outage";
 *       "app" means "application", and so on;</li>
 *   <li>a few extra filler words that say nothing about the problem ("make", "get", "use").</li>
 * </ol>
 * With this, the specification's own example ("Payment application unavailable", "Payment transaction failing",
 * "Unable to make payment") scores 0.5 or 0.67 for every pair instead of 0.2 or 0, above the default threshold of 0.3.
 */
@Component
@Primary
public class NormalizedJaccardSimilarityStrategy implements SimilarityStrategy {

    private static final Set<String> EXTRA_STOP_WORDS = Set.of("make", "get", "use", "using", "any", "all", "some",
            "when", "while", "since", "again", "still", "very", "now", "today", "just", "also");

    /** Word (after stemming) to the keyword of its group. */
    private static final Map<String, String> SYNONYMS = Map.ofEntries(
            Map.entry("unavailable", "outage"), Map.entry("down", "outage"), Map.entry("outage", "outage"),
            Map.entry("fail", "outage"), Map.entry("failure", "outage"), Map.entry("error", "outage"),
            Map.entry("unable", "outage"), Map.entry("broken", "outage"), Map.entry("crash", "outage"),
            Map.entry("offline", "outage"), Map.entry("unreachable", "outage"), Map.entry("dead", "outage"),
            Map.entry("stopp", "outage"), Map.entry("stop", "outage"),
            Map.entry("app", "application"), Map.entry("application", "application"),
            Map.entry("pay", "payment"), Map.entry("payment", "payment"),
            Map.entry("mail", "email"), Map.entry("email", "email"),
            Map.entry("logon", "login"), Map.entry("signin", "login"), Map.entry("login", "login"),
            Map.entry("slow", "slow"), Map.entry("sluggish", "slow"), Map.entry("lag", "slow"),
            Map.entry("latency", "slow"), Map.entry("timeout", "slow"));

    @Override
    public double similarity(String a, String b) {
        Set<String> first = keywords(a);
        Set<String> second = keywords(b);
        Set<String> union = new HashSet<>(first);
        union.addAll(second);
        if (union.isEmpty()) {
            return 0.0;
        }
        Set<String> shared = new HashSet<>(first);
        shared.retainAll(second);
        return (double) shared.size() / union.size();
    }

    /** The normalised keywords of a text. */
    static Set<String> keywords(String text) {
        Set<String> result = new LinkedHashSet<>();
        for (String token : TextTokenizer.tokens(text)) {
            if (EXTRA_STOP_WORDS.contains(token)) {
                continue;
            }
            String stem = stem(token);
            result.add(SYNONYMS.getOrDefault(stem, stem));
        }
        return result;
    }

    /** Strips the commonest English endings; deliberately simple and only applied to longer words. */
    static String stem(String word) {
        if (word.length() >= 6 && word.endsWith("ing")) {
            return word.substring(0, word.length() - 3);
        }
        if (word.length() >= 5 && word.endsWith("ed")) {
            return word.substring(0, word.length() - 2);
        }
        if (word.length() >= 5 && word.endsWith("ies")) {
            return word.substring(0, word.length() - 3) + "y";
        }
        if (word.length() >= 4 && word.endsWith("s") && !word.endsWith("ss") && !word.endsWith("us")) {
            return word.substring(0, word.length() - 1);
        }
        return word;
    }
}
