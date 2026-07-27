package com.chatdiet.recipe;

public record LogRecipeRequest(
        String recipeName,
        int totalCalories,
        double totalProteinG,
        double totalCarbsG,
        double totalFatG
) {
}
