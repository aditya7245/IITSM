package com.iitsm.service;

import com.iitsm.domain.catalog.Category;
import com.iitsm.util.TextTokenizer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Suggests incident categories from a description by counting keyword matches.
 * Each category stores its own comma-separated keywords in the database, so a new category (with keywords)
 * is suggested automatically, with no code change. Keywords must be single words.
 */
@Component
public class CategorySuggester {

    /**
     * @return categories with at least one keyword in the text, best match first; ties go to the lower category id.
     *         Inactive categories are skipped. The list is empty if nothing matches.
     */
    public List<CategoryScore> suggest(String text, List<Category> categories) {
        Set<String> words = TextTokenizer.tokens(text);
        if (words.isEmpty() || categories == null) {
            return List.of();
        }
        List<CategoryScore> scores = new ArrayList<>();
        for (Category category : categories) {
            if (!category.isActive()) {
                continue;
            }
            int hits = countHits(category.getKeywords(), words);
            if (hits > 0) {
                scores.add(new CategoryScore(category, hits));
            }
        }
        scores.sort(Comparator.comparingInt(CategoryScore::hits).reversed()
                .thenComparing(score -> score.category().getId(), Comparator.nullsLast(Comparator.naturalOrder())));
        return scores;
    }

    /** Number of distinct keywords (from the comma-separated list) that appear among the words of the text. */
    private static int countHits(String keywordList, Set<String> words) {
        if (keywordList == null || keywordList.isBlank()) {
            return 0;
        }
        Set<String> keywords = new HashSet<>();
        for (String part : keywordList.split(",")) {
            String keyword = part.trim().toLowerCase(Locale.ROOT);
            if (!keyword.isEmpty()) {
                keywords.add(keyword);
            }
        }
        int hits = 0;
        for (String keyword : keywords) {
            if (words.contains(keyword)) {
                hits++;
            }
        }
        return hits;
    }
}
