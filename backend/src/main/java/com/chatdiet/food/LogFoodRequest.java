package com.chatdiet.food;

import java.time.LocalDateTime;

/**
 * Request for the {@code log_food} tool: a named food entry, optionally with a weighed gram
 * amount and/or a natural quantity+unit, and the model's best-effort estimate of
 * calories/macros/micronutrients.
 *
 * <p>The estimate fields are only used as a last resort - {@link LogFoodTool} tries, in order:
 * (1) the {@code FOOD_ITEM} cache scaled by {@code amountGrams}, (2) a USDA FoodData Central
 * lookup scaled by {@code amountGrams}, (3) the cache or FDC scaled by a known
 * {@code quantity}/{@code unit} portion weight (e.g. "1 medium banana" -> 118g, from
 * {@code PORTION_UNIT}), before falling back to these model-supplied numbers. Still always
 * populate them: they become the fallback whenever none of the above resolves.
 *
 * @param amountGrams   the weighed portion size in grams, if the user stated one (e.g. "42g of
 *                      celery"); null if this is not gram-based
 * @param quantity      how many of {@code unit} were eaten (e.g. {@code 2} for "2 eggs",
 *                      {@code 1} for "1 medium banana"); null if not a natural-unit quantity, or
 *                      if {@code amountGrams} was given instead. Can be given *together* with
 *                      {@code amountGrams} when the user states both (e.g. "2 eggs, about
 *                      100g") - that teaches the app that unit's real weight for next time.
 * @param unit          the size/measure word alone, e.g. "medium", "large", "cup", "slice",
 *                      "egg" - never repeating the food name; null unless {@code quantity} is
 *                      also given
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
        Double quantity,
        String unit,
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
