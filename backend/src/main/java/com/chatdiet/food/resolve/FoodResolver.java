package com.chatdiet.food.resolve;

import com.chatdiet.fdc.FdcClient;
import com.chatdiet.fooditem.FoodAlias;
import com.chatdiet.fooditem.FoodAliasNormalizer;
import com.chatdiet.fooditem.FoodAliasRepository;
import com.chatdiet.fooditem.FoodItem;
import com.chatdiet.fooditem.FoodItemRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Resolves a spoken/typed food name to a cached {@link com.chatdiet.fooditem.FoodItem}. Two
 * lookups, one exact and one fuzzy: alias resolution ({@code WHERE alias_normalized = ?}) resolves
 * silently; on a miss, fuzzy scoring auto-accepts a single clear winner (thresholds below - a
 * 2026-09 amendment to §4.2's original never-auto-select rule, per the user's
 * speed-over-precision philosophy) and otherwise produces a numbered list for a human to pick
 * from. See docs/fixing-substring-problem-spec.md §4.2 and its Amendments section.
 */
@Service
public class FoodResolver {

    private static final int MAX_CANDIDATES = 5;

    /** Scores at or above this (name-exact 1.0, plural flip 0.95) auto-accept outright. */
    private static final double AUTO_ACCEPT_MIN = 0.95;

    /**
     * Substring and strong-typo tier: auto-accepts only for a sole candidate that also clears
     * {@link #wordGuardAllows} - "wheat bread" may become "Whole Wheat Bread", but a bare
     * "chicken" never silently becomes "chicken salad sandwich".
     */
    private static final double AUTO_ACCEPT_GUARDED_MIN = 0.8;

    /** With multiple candidates, the top auto-accepts only when the runner-up sits below this. */
    private static final double RUNNER_UP_CEILING = 0.8;

    private final FoodAliasRepository foodAliasRepository;
    private final FoodItemRepository foodItemRepository;
    private final FdcClient fdcClient;
    private final FuzzyCandidateGenerator fuzzyCandidateGenerator;

    public FoodResolver(FoodAliasRepository foodAliasRepository, FoodItemRepository foodItemRepository,
                         FdcClient fdcClient, FuzzyCandidateGenerator fuzzyCandidateGenerator) {
        this.foodAliasRepository = foodAliasRepository;
        this.foodItemRepository = foodItemRepository;
        this.fdcClient = fdcClient;
        this.fuzzyCandidateGenerator = fuzzyCandidateGenerator;
    }

    public FoodResolution resolve(String foodRef) {
        var normalized = FoodAliasNormalizer.normalize(foodRef);

        var exact = resolveExact(foodRef);
        if (exact.isPresent()) {
            return new FoodResolution.Resolved(exact.get());
        }
        // No alias, or the alias points at a soft-deleted item - fall through to candidates.

        var activeItems = foodItemRepository.findAll().stream()
                .filter(i -> i.deletedAt() == null)
                .toList();
        var scored = fuzzyCandidateGenerator.scoreCachedItems(normalized, activeItems);

        var auto = autoAccept(normalized, scored);
        if (auto.isPresent()) {
            return new FoodResolution.AutoResolved(auto.get());
        }

        var cachedMatches = scored.stream().map(FuzzyCandidateGenerator.ScoredMatch::item).toList();
        if (cachedMatches.size() >= 2) {
            // Cached-only per §4.2 - mixing in FDC here would blur "ambiguous between what's
            // already vetted" with "unknown, here's what FDC has."
            return new FoodResolution.Ambiguous(cachedMatches.stream().map(Candidate::cached).toList());
        }

        var candidates = new ArrayList<Candidate>();
        cachedMatches.forEach(item -> candidates.add(Candidate.cached(item)));
        var remainingSlots = Math.max(0, MAX_CANDIDATES - candidates.size());
        if (remainingSlots > 0) {
            fdcClient.search(foodRef, remainingSlots).forEach(fdc -> candidates.add(Candidate.fdc(fdc)));
        }
        candidates.add(Candidate.estimateOption());
        return new FoodResolution.Unknown(candidates);
    }

    /**
     * The single clear winner among the scored matches, if the thresholds say one exists.
     * A near-tie at the top (two scores at or above {@link #AUTO_ACCEPT_MIN}) is resolved toward
     * the higher-calorie candidate rather than asking - the user's stated preference is to
     * overestimate a few percent, never to be interrupted.
     */
    private Optional<FoodItem> autoAccept(String normalizedQuery, List<FuzzyCandidateGenerator.ScoredMatch> scored) {
        if (scored.isEmpty()) {
            return Optional.empty();
        }
        var top = scored.get(0);
        if (scored.size() == 1) {
            if (top.score() >= AUTO_ACCEPT_MIN
                    || (top.score() >= AUTO_ACCEPT_GUARDED_MIN && wordGuardAllows(normalizedQuery, top.item()))) {
                return Optional.of(top.item());
            }
            return Optional.empty();
        }
        var runnerUp = scored.get(1);
        if (top.score() >= AUTO_ACCEPT_MIN && runnerUp.score() >= AUTO_ACCEPT_MIN) {
            if (top.score() > runnerUp.score()) {
                // e.g. a name-exact 1.0 over a plural flip 0.95 - the strictly better match wins.
                return Optional.of(top.item());
            }
            return scored.stream()
                    .filter(s -> s.score() == top.score())
                    .map(FuzzyCandidateGenerator.ScoredMatch::item)
                    .max(Comparator.comparingDouble(i -> i.per100gCalories() != null ? i.per100gCalories() : 0.0));
        }
        if (top.score() >= AUTO_ACCEPT_MIN && runnerUp.score() < RUNNER_UP_CEILING) {
            return Optional.of(top.item());
        }
        return Optional.empty();
    }

    /**
     * A candidate may be at most one word longer than the query - the guard that keeps a generic
     * phrase from silently becoming a much more specific dish with very different per-100g values
     * ("chicken" vs. "chicken salad sandwich"), while still allowing the one-word elaboration
     * ("wheat bread" vs. "whole wheat bread").
     */
    private boolean wordGuardAllows(String normalizedQuery, FoodItem candidate) {
        return wordCount(FoodAliasNormalizer.normalize(candidate.name())) - wordCount(normalizedQuery) <= 1;
    }

    private static int wordCount(String text) {
        var trimmed = text.trim();
        return trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
    }

    /**
     * Exact-alias lookup only - no fuzzy matching, no FDC network call. For callers that need
     * the cheap deterministic answer and handle a miss themselves (the estimate-override guard,
     * the fast log path).
     */
    public Optional<FoodItem> resolveExact(String foodRef) {
        var normalized = FoodAliasNormalizer.normalize(foodRef);
        return foodAliasRepository.findByAliasNormalized(normalized)
                .flatMap(alias -> foodItemRepository.findById(alias.foodItemId()))
                .filter(i -> i.deletedAt() == null);
    }

    /**
     * Permanently maps this phrase to the given item so it resolves exactly next time. Every
     * clarification pick learns - ambiguous ones included, a 2026-09 amendment to §4.3: a bad
     * mapping is deletable in the alias UI, whereas re-asking the same question forever was the
     * top source of logging friction. Skips silently when the normalized key already exists -
     * either the phrase already points somewhere (racing turns) or this pick just re-confirmed it.
     */
    public void learnAlias(String foodRef, long foodItemId, String source) {
        var normalized = FoodAliasNormalizer.normalize(foodRef);
        if (foodAliasRepository.findByAliasNormalized(normalized).isPresent()) {
            return;
        }
        foodAliasRepository.save(new FoodAlias(normalized, foodItemId, source));
    }
}
