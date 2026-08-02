package com.chatdiet.recipe;

import java.util.List;

/**
 * Request payload for {@link CreateRecipeTool}: batch- or serving-level totals for a new recipe,
 * to be normalized to per-100g values.
 *
 * @param totalWeightG    weight in grams the totals below were measured over (e.g. the whole pot,
 *                        or a single serving if that's all that's known)
 * @param provisional     {@code true} if these totals came from a single serving rather than a
 *                        confident whole-batch weighing
 * @param typicalServingG the recipe's usual serving size in grams, or {@code null} if unknown
 */
public record CreateRecipeRequest(
        String name,
        double totalWeightG,
        double totalCalories,
        double totalProteinG,
        double totalCarbsG,
        double totalFatG,
        Double typicalServingG,
        boolean provisional,
        List<RecipeIngredientInput> ingredients
) {
}
