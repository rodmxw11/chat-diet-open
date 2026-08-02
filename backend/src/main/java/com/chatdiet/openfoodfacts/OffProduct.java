package com.chatdiet.openfoodfacts;

/**
 * Simplified, application-facing view of a product returned by {@link OpenFoodFactsClient},
 * with nutrition values normalized to per-100g figures.
 *
 * @param typicalServingG Open Food Facts' reported serving size in grams; may be {@code null}
 *                         if the product doesn't declare one
 */
public record OffProduct(
        String name,
        Double caloriesPer100g,
        Double proteinPer100g,
        Double carbsPer100g,
        Double fatPer100g,
        Double typicalServingG
) {
}
