package com.chatdiet.food;

/**
 * Request for the {@code log_food} tool: a named food entry with model-estimated calories and
 * macros.
 */
public record LogFoodRequest(
        String description,
        int totalCalories,
        double totalProteinG,
        double totalCarbsG,
        double totalFatG
) {
}
