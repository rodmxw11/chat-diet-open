package com.chatdiet.food;

import java.time.LocalDateTime;

/**
 * Request for the {@code log_food} tool: a named food entry with model-estimated calories and
 * macros.
 *
 * @param loggedAt when this was actually eaten, if the user mentioned a past day and/or meal
 *                 (e.g. "yesterday for dinner"); null to log under the current time
 */
public record LogFoodRequest(
        String description,
        int totalCalories,
        double totalProteinG,
        double totalCarbsG,
        double totalFatG,
        LocalDateTime loggedAt
) {
}
