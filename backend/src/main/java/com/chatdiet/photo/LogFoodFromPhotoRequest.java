package com.chatdiet.photo;

/**
 * Request for the {@code log_food_from_photo} tool, carrying the final calorie/macro totals
 * (after any clarification round-trip and arithmetic) for the food shown in the attached photo.
 */
public record LogFoodFromPhotoRequest(
        String description,
        int totalCalories,
        double totalProteinG,
        double totalCarbsG,
        double totalFatG
) {
}
