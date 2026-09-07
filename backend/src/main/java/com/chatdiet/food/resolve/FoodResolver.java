package com.chatdiet.food.resolve;

import com.chatdiet.fdc.FdcClient;
import com.chatdiet.fooditem.FoodAlias;
import com.chatdiet.fooditem.FoodAliasNormalizer;
import com.chatdiet.fooditem.FoodAliasRepository;
import com.chatdiet.fooditem.FoodItem;
import com.chatdiet.fooditem.FoodItemRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Optional;

/**
 * Resolves a spoken/typed food name to a cached {@link com.chatdiet.fooditem.FoodItem}. Two
 * lookups, one exact and one fuzzy, and the distinction is the design: alias resolution
 * ({@code WHERE alias_normalized = ?}) is the only path that writes a row without asking, so it's
 * incapable of guessing; candidate generation (fuzzy, on a miss) never auto-selects at any score -
 * it produces a numbered list for a human to pick from. See
 * docs/fixing-substring-problem-spec.md §4.2.
 */
@Service
public class FoodResolver {

    private static final int MAX_CANDIDATES = 5;

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
        var cachedMatches = fuzzyCandidateGenerator.matchCachedItems(normalized, activeItems);

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
     * Records the user's clarification-turn pick as a permanent alias - but only when the original
     * phrase was {@link FoodResolution.Unknown}. An {@link FoodResolution.Ambiguous} phrase stays
     * ambiguous next time by design: writing an alias there would make the other candidate
     * unreachable by that phrase forever, the same silent corruption relocated from the matcher
     * into the alias table.
     */
    public void confirmSelection(String foodRef, long foodItemId, boolean wasAmbiguous) {
        if (wasAmbiguous) {
            return;
        }
        var normalized = FoodAliasNormalizer.normalize(foodRef);
        if (foodAliasRepository.findByAliasNormalized(normalized).isPresent()) {
            return;
        }
        foodAliasRepository.save(new FoodAlias(normalized, foodItemId, "USER"));
    }
}
