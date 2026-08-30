package com.chatdiet.food.resolve;

/** The outcome of {@link QuantityResolver#resolve}. */
public sealed interface QuantityResolution {

    /**
     * @param teachUnitCount count of {@code teachUnitName} implied alongside the explicit gram
     *                       figure (e.g. "2" for "2 eggs, 100g"), or {@code null} if the phrase
     *                       stated grams only - lets the caller teach {@code PORTION_UNIT} that
     *                       unit's real weight for next time
     * @param teachUnitName  the unit word implied alongside the grams, or {@code null}
     */
    record Grams(double grams, Double teachUnitCount, String teachUnitName) implements QuantityResolution {
        public Grams(double grams) {
            this(grams, null, null);
        }
    }

    record Unresolvable() implements QuantityResolution {
    }
}
