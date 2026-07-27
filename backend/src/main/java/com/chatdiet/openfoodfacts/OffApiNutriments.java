package com.chatdiet.openfoodfacts;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
record OffApiNutriments(
        @JsonProperty("energy-kcal_100g") Double caloriesPer100g,
        @JsonProperty("proteins_100g") Double proteinPer100g,
        @JsonProperty("carbohydrates_100g") Double carbsPer100g,
        @JsonProperty("fat_100g") Double fatPer100g
) {
}
