package com.chatdiet.food;

/**
 * Request for the {@code correct_food_entry} tool. Any {@code null} field leaves the
 * corresponding value on the existing entry unchanged.
 *
 * @param amountGrams a corrected weighed amount (e.g. "make that 50g"). When present and the
 *                    entry being corrected has a {@code foodItemId}, the tool recomputes
 *                    calories/macros/micronutrients from that cached item's per-100g values
 *                    scaled to this new amount, ignoring any of the direct-override fields above.
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
        Double potassiumMg,
        Double amountGrams
) {
}
