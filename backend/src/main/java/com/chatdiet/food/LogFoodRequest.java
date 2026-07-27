package com.chatdiet.food;

public record LogFoodRequest(
        String description,
        int totalCalories,
        double totalProteinG,
        double totalCarbsG,
        double totalFatG
) {
}
