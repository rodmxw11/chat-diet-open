package com.chatdiet.recipe;

public record RefineRecipeRequest(
        String name,
        Double totalWeightG,
        Double totalCalories,
        Double totalProteinG,
        Double totalCarbsG,
        Double totalFatG,
        Double typicalServingG
) {
}
