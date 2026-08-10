package com.chatdiet.photo;

import java.time.LocalDateTime;

/**
 * Request for the {@code log_food_from_photo} tool, carrying the final calorie/macro totals
 * (after any clarification round-trip and arithmetic) for the food shown in the attached photo.
 *
 * @param loggedAt when this was actually eaten, if the user mentioned a past day and/or meal
 *                 (e.g. "yesterday for dinner"); null to log under the current time
 */
public record LogFoodFromPhotoRequest(
        String description,
        int totalCalories,
        double totalProteinG,
        double totalCarbsG,
        double totalFatG,
        LocalDateTime loggedAt
) {
}
