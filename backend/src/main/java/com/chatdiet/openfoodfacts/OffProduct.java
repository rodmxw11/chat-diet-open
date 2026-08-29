package com.chatdiet.openfoodfacts;

/**
 * Simplified, application-facing view of a product returned by {@link OpenFoodFactsClient},
 * with nutrition values normalized to per-100g figures.
 *
 * @param sodiumMgPer100g      per-100g sodium in milligrams (converted from OFF's grams)
 * @param cholesterolMgPer100g per-100g cholesterol in milligrams (converted from OFF's grams)
 * @param potassiumMgPer100g   per-100g potassium in milligrams (converted from OFF's grams)
 * @param typicalServingG Open Food Facts' reported serving size in grams; may be {@code null}
 *                         if the product doesn't declare one
 */
public record OffProduct(
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
