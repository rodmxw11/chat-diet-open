package com.chatdiet.fdc;

import java.util.List;

/**
 * Maps FoodData Central's detail-endpoint nutrient shape ({@code foodNutrients[].nutrient.id},
 * {@code foodNutrients[].amount}) to the app's per-100g fields.
 *
 * <p>Four rules: (1) match on nutrient ID only, never name - a food can carry the same nutrient
 * more than once in different units/contexts (e.g. 1008 kcal alongside 1062 kJ, or Foundation's
 * 2047/2048 Atwater energy figures), and scanning for the first name starting with "Energy" risks
 * reading whichever FDC happened to list first. (2) absent means not reported, not zero - mapped
 * to {@code null}, never {@code 0.0}, so an unknown value doesn't silently read as a real zero.
 * (3) assert the reported unit matches what's expected; throw on mismatch rather than risk a value
 * off by 1000x if FDC ever changes a unit. (4) a {@code servingSizeUnit} of {@code "ml"} on a
 * liquid is left unmapped to grams rather than guessed at with an assumed density - a visible gap
 * on the Food Items page beats a wrong number nobody sees.
 */
final class FdcNutrientMapper {

    private static final int CALORIES = 1008;
    private static final int PROTEIN = 1003;
    private static final int CARBS = 1005;
    private static final int FAT = 1004;
    private static final int FIBER = 1079;
    private static final int SODIUM_MG = 1093;
    private static final int SATURATED_FAT = 1258;
    private static final int CHOLESTEROL_MG = 1253;
    private static final int POTASSIUM_MG = 1092;
    private static final int SUGAR_PRIMARY = 2000;
    private static final int SUGAR_FALLBACK = 1063;

    private FdcNutrientMapper() {
    }

    /** @return the mapped product, or {@code null} if the detail has no usable calorie data */
    static FdcProduct map(FdcApiFoodDetail detail) {
        var nutrients = detail.foodNutrients();
        Double calories = extract(nutrients, CALORIES, "KCAL");
        if (calories == null) {
            return null;
        }

        Double sugar = extract(nutrients, SUGAR_PRIMARY, "G");
        if (sugar == null) {
            sugar = extract(nutrients, SUGAR_FALLBACK, "G");
        }

        Double servingG = "g".equalsIgnoreCase(detail.servingSizeUnit()) ? detail.servingSize() : null;

        return new FdcProduct(
                detail.description(),
                calories,
                extract(nutrients, PROTEIN, "G"),
                extract(nutrients, CARBS, "G"),
                extract(nutrients, FAT, "G"),
                extract(nutrients, FIBER, "G"),
                sugar,
                extract(nutrients, SODIUM_MG, "MG"),
                extract(nutrients, SATURATED_FAT, "G"),
                extract(nutrients, CHOLESTEROL_MG, "MG"),
                extract(nutrients, POTASSIUM_MG, "MG"),
                servingG,
                detail.fdcId());
    }

    private static Double extract(List<FdcApiFoodNutrientDetail> nutrients, int nutrientId, String expectedUnit) {
        if (nutrients == null) return null;
        return nutrients.stream()
                .filter(n -> n.nutrient() != null && n.nutrient().id() != null && n.nutrient().id() == nutrientId)
                .findFirst()
                .map(n -> {
                    var unit = n.nutrient().unitName();
                    if (unit != null && !unit.equalsIgnoreCase(expectedUnit)) {
                        throw new IllegalStateException("Nutrient %d expected unit %s but FDC reported %s"
                                .formatted(nutrientId, expectedUnit, unit));
                    }
                    return n.amount();
                })
                .orElse(null);
    }
}
