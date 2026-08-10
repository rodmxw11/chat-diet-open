package com.chatdiet.recipe;

import java.time.LocalDateTime;

/**
 * Request payload for {@link LogRecipeTool}: final nutrition totals for the amount of a recipe
 * actually eaten (already scaled from the recipe's per-100g values, plus any extras), to be
 * logged as one food entry.
 *
 * @param loggedAt when this was actually eaten, if the user mentioned a past day and/or meal
 *                 (e.g. "yesterday for dinner"); null to log under the current time
 */
public record LogRecipeRequest(
        String recipeName,
        int totalCalories,
        double totalProteinG,
        double totalCarbsG,
        double totalFatG,
        LocalDateTime loggedAt
) {
}
