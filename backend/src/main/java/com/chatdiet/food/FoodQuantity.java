package com.chatdiet.food;

import java.util.OptionalDouble;

/**
 * Some models fill an unspecified numeric argument with 0 rather than omitting it, so a
 * literal 0 in either field is treated as "not provided" rather than a real quantity.
 */
final class FoodQuantity {

    private FoodQuantity() {
    }

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
