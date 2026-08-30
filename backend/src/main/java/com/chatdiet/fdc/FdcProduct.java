package com.chatdiet.fdc;

/**
 * Simplified, application-facing view of a food's nutrition from {@link FdcClient#fetchDetail},
 * normalized to per-100g figures - mirrors {@link com.chatdiet.openfoodfacts.OffProduct}'s shape
 * so both lookup tiers feed the same downstream caching/scaling code. Unlike Open Food Facts, FDC
 * already reports sodium/cholesterol/potassium in milligrams natively, so no unit conversion is
 * needed here.
 *
 * @param typicalServingG the label's serving size in grams, or {@code null} if FDC reported none
 *                         or reported it in a non-gram unit (e.g. {@code ml} on a liquid) - see
 *                         {@link FdcNutrientMapper}. A {@code null} here is treated as "assume
 *                         100g servings" by callers.
 * @param fdcId           this food's FDC identifier
 */
public record FdcProduct(
        String name,
        Double caloriesPer100g,
        Double proteinPer100g,
        Double carbsPer100g,
        Double fatPer100g,
        Double fiberPer100g,
        Double sugarPer100g,
        Double sodiumMgPer100g,
        Double saturatedFatPer100g,
        Double cholesterolMgPer100g,
        Double potassiumMgPer100g,
        Double typicalServingG,
        Long fdcId
) {
}
