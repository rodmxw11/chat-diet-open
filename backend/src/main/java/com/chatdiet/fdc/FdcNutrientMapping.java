package com.chatdiet.fdc;

import java.util.List;

/**
 * Maps FoodData Central's numeric nutrient IDs to the app's per-100g nutrient fields. FDC does
 * not return named fields the way Open Food Facts' product JSON does - each food has a flat
 * {@code foodNutrients} array keyed by these IDs instead.
 *
 * <p>IDs are FDC's internal {@code nutrientId} (stable across Foundation Foods and SR Legacy in
 * the current FDC database), not the legacy {@code nutrientNumber} string field.
 */
final class FdcNutrientMapping {

    static final int CALORIES = 1008;
    static final int PROTEIN = 1003;
    static final int CARBS = 1005;
    static final int FAT = 1004;
    static final int FIBER = 1079;
    static final int SUGAR = 2000;
    static final int SODIUM_MG = 1093;
    static final int SATURATED_FAT = 1258;
    static final int CHOLESTEROL_MG = 1253;
    static final int POTASSIUM_MG = 1092;

    private FdcNutrientMapping() {
    }

    /** Returns the per-100g value of the given nutrient ID, or {@code null} if not reported. */
    static Double extract(List<FdcApiNutrient> nutrients, int nutrientId) {
        if (nutrients == null) return null;
        return nutrients.stream()
                .filter(n -> n.nutrientId() != null && n.nutrientId() == nutrientId)
                .map(FdcApiNutrient::value)
                .findFirst()
                .orElse(null);
    }
}
