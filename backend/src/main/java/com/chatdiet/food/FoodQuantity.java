package com.chatdiet.food;

import java.util.OptionalDouble;

/**
 * Resolves how much of a cached/UPC food item was consumed, in grams. Some models fill an
 * unspecified numeric argument with 0 rather than omitting it, so a literal 0 in either
 * {@code quantityG} or {@code quantityServings} is treated as "not provided" rather than a
 * real quantity.
 */
final class FoodQuantity {

    private FoodQuantity() {
    }

    /**
     * Resolves the consumed quantity to grams, preferring an explicit gram amount over a
     * serving count.
     *
     * @param quantityG        explicit grams eaten; ignored if {@code null} or {@code <= 0}
     * @param quantityServings number of typical servings eaten, used only if {@code quantityG}
     *                         is not provided; ignored if {@code null} or {@code <= 0}
     * @param typicalServingG  grams per typical serving for this food item; defaults to 100.0
     *                         if {@code null} when needed to scale {@code quantityServings}
     * @return the resolved grams, or empty if neither quantity was meaningfully provided
     */
    static OptionalDouble resolveGrams(Double quantityG, Double quantityServings, Double typicalServingG) {
        if (quantityG != null && quantityG > 0) {
            return OptionalDouble.of(quantityG);
        }
        if (quantityServings != null && quantityServings > 0) {
            return OptionalDouble.of(quantityServings * (typicalServingG != null ? typicalServingG : 100.0));
        }
        return OptionalDouble.empty();
    }
}
