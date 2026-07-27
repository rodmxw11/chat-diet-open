package com.chatdiet.openfoodfacts;

public record OffProduct(
        String name,
        Double caloriesPer100g,
        Double proteinPer100g,
        Double carbsPer100g,
        Double fatPer100g,
        Double typicalServingG
) {
}
