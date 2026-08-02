package com.chatdiet.recipe;

/**
 * Request payload for {@link LogRecipeTool}: final nutrition totals for the amount of a recipe
 * actually eaten (already scaled from the recipe's per-100g values, plus any extras), to be
 * logged as one food entry.
 */
public record LogRecipeRequest(
        String recipeName,
        int totalCalories,
        double totalProteinG,
        double totalCarbsG,
        double totalFatG
) {
}
