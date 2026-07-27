package com.chatdiet.photo;

public record LogFoodFromPhotoRequest(
        String description,
        int totalCalories,
        double totalProteinG,
        double totalCarbsG,
        double totalFatG
) {
}
