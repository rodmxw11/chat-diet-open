package com.chatdiet.fdc;

/**
 * Simplified, application-facing view of a food returned by {@link FdcClient}, with nutrition
 * values normalized to per-100g figures - mirrors
 * {@link com.chatdiet.openfoodfacts.OffProduct}'s shape so both lookup tiers feed the same
 * downstream caching/scaling code. Unlike Open Food Facts, FDC already reports sodium/
 * cholesterol/potassium in milligrams natively, so no unit conversion is needed here.
 *
 * @param typicalServingG FDC's basic search response doesn't include portion data; always
 *                         {@code null} today, which {@link com.chatdiet.food.FoodQuantity}
 *                         already treats as "assume 100g servings".
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
        Double typicalServingG
) {
}
