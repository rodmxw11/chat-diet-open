package com.chatdiet.food;

/**
 * Request for the {@code correct_food_entry} tool. Any {@code null} field leaves the
 * corresponding value on the existing entry unchanged.
 */
public record CorrectFoodRequest(
        Integer totalCalories,
        Double totalProteinG,
        Double totalCarbsG,
        Double totalFatG,
        Double fiberG,
        Double sugarG,
        Double sodiumMg,
        Double saturatedFatG,
        Double cholesterolMg,
        Double potassiumMg
) {
}
