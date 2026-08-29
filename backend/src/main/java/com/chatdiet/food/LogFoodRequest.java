package com.chatdiet.food;

import java.time.LocalDateTime;

/**
 * Request for the {@code log_food} tool: a named food entry with model-estimated calories,
 * macros, and micronutrients.
 *
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
        int totalCalories,
        double totalProteinG,
        double totalCarbsG,
        double totalFatG,
        double fiberG,
        double sugarG,
        double sodiumMg,
        double saturatedFatG,
        double cholesterolMg,
        double potassiumMg,
        LocalDateTime loggedAt
) {
}
