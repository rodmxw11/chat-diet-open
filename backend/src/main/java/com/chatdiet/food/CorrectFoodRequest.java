package com.chatdiet.food;

public record CorrectFoodRequest(
        Integer totalCalories,
        Double totalProteinG,
        Double totalCarbsG,
        Double totalFatG
) {
}
