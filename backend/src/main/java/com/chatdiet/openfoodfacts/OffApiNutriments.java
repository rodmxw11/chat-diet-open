package com.chatdiet.openfoodfacts;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Jackson mapping of the {@code nutriments} object in an Open Food Facts API v2 product response.
 *
 * <p>OFF reports sodium/cholesterol/potassium per-100g in grams (same convention as fat/protein/
 * carbs), not milligrams - {@link OpenFoodFactsClient} converts those three to mg when building
 * an {@link OffProduct}, to match this app's mg-based fields elsewhere.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record OffApiNutriments(
        @JsonProperty("energy-kcal_100g") Double caloriesPer100g,
        @JsonProperty("proteins_100g") Double proteinPer100g,
        @JsonProperty("carbohydrates_100g") Double carbsPer100g,
        @JsonProperty("fat_100g") Double fatPer100g,
        @JsonProperty("fiber_100g") Double fiberPer100g,
        @JsonProperty("sugars_100g") Double sugarPer100g,
        @JsonProperty("sodium_100g") Double sodiumGPer100g,
        @JsonProperty("saturated-fat_100g") Double saturatedFatPer100g,
        @JsonProperty("cholesterol_100g") Double cholesterolGPer100g,
        @JsonProperty("potassium_100g") Double potassiumGPer100g
) {
}
