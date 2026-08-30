package com.chatdiet.food.resolve;

import com.chatdiet.fooditem.FoodAliasNormalizer;
import com.chatdiet.fooditem.FoodItem;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Scores cached {@link FoodItem}s against a normalized query for {@link FoodResolver}'s candidate
 * generation - never an auto-selector, purely a ranked, inclusion-thresholded list a human picks
 * from. Combines substring containment, Levenshtein edit-distance similarity, a naive
 * singular/plural flip, and token overlap; no stemming or other meaning-changing normalization
 * (that's {@link FoodAliasNormalizer}'s job, and deliberately narrower there too).
 *
 * <p>Deliberately not Jaro-Winkler: it scores short, letter-sharing but otherwise unrelated words
 * (e.g. "banana"/"lasagna") too highly to be a useful inclusion signal here.
 */
@Component
public class FuzzyCandidateGenerator {

    private static final double MIN_SIMILARITY = 0.6;

    private final LevenshteinDistance levenshtein = LevenshteinDistance.getDefaultInstance();

    /** Returns active items plausibly matching the query, ranked most-similar first. */
    public List<FoodItem> matchCachedItems(String normalizedQuery, List<FoodItem> activeItems) {
        record Scored(FoodItem item, double score) {
        }
        return activeItems.stream()
                .map(item -> new Scored(item, score(normalizedQuery, FoodAliasNormalizer.normalize(item.name()))))
                .filter(scored -> scored.score() >= MIN_SIMILARITY)
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .map(Scored::item)
                .toList();
    }

    private double score(String query, String candidate) {
        if (query.isEmpty() || candidate.isEmpty()) {
            return 0.0;
        }
        if (query.equals(candidate)) {
            return 1.0;
        }
        if (pluralFlipMatches(query, candidate)) {
            return 0.95;
        }
        if (candidate.contains(query) || query.contains(candidate)) {
            return 0.8;
        }
        return Math.max(editSimilarity(query, candidate), tokenOverlap(query, candidate));
    }

    private double editSimilarity(String a, String b) {
        var maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) return 1.0;
        return 1.0 - ((double) levenshtein.apply(a, b) / maxLen);
    }

    private double tokenOverlap(String a, String b) {
        var aTokens = tokenize(a);
        var bTokens = tokenize(b);
        if (aTokens.isEmpty() || bTokens.isEmpty()) return 0.0;
        var intersection = aTokens.stream().filter(bTokens::contains).count();
        var union = aTokens.size() + bTokens.size() - intersection;
        return union == 0 ? 0.0 : (double) intersection / union;
    }

    private Set<String> tokenize(String s) {
        return Set.of(s.trim().split("\\s+"));
    }

    /** Naive plural/singular flip: "oat" vs "oats", "green" vs "greens" - offered, never assumed. */
    private boolean pluralFlipMatches(String a, String b) {
        return (a + "s").equals(b) || (b + "s").equals(a)
                || (a + "es").equals(b) || (b + "es").equals(a);
    }
}
