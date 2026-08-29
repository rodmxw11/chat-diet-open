package com.chatdiet.food;

import java.time.LocalDateTime;

/**
 * Request for the {@code log_food} tool: a named food entry, optionally with a weighed gram
 * amount, and the model's best-effort estimate of calories/macros/micronutrients.
 *
 * <p>The estimate fields are only used as a last resort - {@link LogFoodTool} first tries the
 * {@code FOOD_ITEM} cache and a USDA FoodData Central lookup (both require {@code amountGrams} to
 * scale to) before falling back to these model-supplied numbers. Still always populate them: they
 * become the fallback whenever no cache/FDC match exists or no gram amount was given.
 *
 * @param amountGrams   the weighed portion size in grams, if the user stated one (e.g. "42g of
 *                      celery"); null if this is not gram-based (e.g. eating out)
 * @param totalCalories estimated calories; null only if you genuinely cannot estimate at all
 * @param fiberG        estimated dietary fiber in grams
 * @param sugarG        estimated total sugar in grams
 * @param sodiumMg      estimated sodium in milligrams
 * @param saturatedFatG estimated saturated fat in grams (a subset of totalFatG, not in addition to it)
 * @param cholesterolMg estimated cholesterol in milligrams
 * @param potassiumMg   estimated potassium in milligrams
 * @param loggedAt when this was actually eaten, if the user mentioned a past day and/or meal
 *                 (e.g. "yesterday for dinner"); null to log under the current time
 */
public record LogFoodRequest(
        String description,
        Double amountGrams,
        Integer totalCalories,
        Double totalProteinG,
        Double totalCarbsG,
        Double totalFatG,
        Double fiberG,
        Double sugarG,
        Double sodiumMg,
        Double saturatedFatG,
        Double cholesterolMg,
        Double potassiumMg,
        LocalDateTime loggedAt
) {
}
