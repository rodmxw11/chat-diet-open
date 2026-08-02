package com.chatdiet.recipe;

/**
 * Request payload for {@link RefineRecipeTool}: updated batch totals for an existing recipe.
 * All fields besides {@code name} are optional - {@code null} leaves the corresponding recipe
 * value unchanged. Per-100g nutrition is only recomputed when both {@code totalWeightG} and
 * {@code totalCalories} are supplied.
 *
 * @param totalWeightG weight in grams the totals below were measured over
 */
public record RefineRecipeRequest(
        String name,
        Double totalWeightG,
        Double totalCalories,
        Double totalProteinG,
        Double totalCarbsG,
        Double totalFatG,
        Double typicalServingG
) {
}
