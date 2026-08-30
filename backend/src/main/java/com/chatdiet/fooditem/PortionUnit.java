package com.chatdiet.fooditem;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;

/**
 * A known gram weight for a named unit of a specific {@link FoodItem} (e.g. "medium" -> 118g for
 * a cached banana), so a natural-language quantity ("1 medium banana") can resolve to grams
 * deterministically instead of falling to the model's own estimate every time.
 *
 * @param unitName normalized (lowercase, trimmed, parenthetical detail stripped) unit/size word,
 *                 e.g. "medium", "cup, sliced", "egg" - not the food name itself
 * @param source   where this portion came from: {@code "FDC"} (USDA FoodData Central's
 *                 {@code foodPortions}) or {@code "WEIGHED"} (derived from the user stating both
 *                 a quantity+unit and a gram amount in the same entry)
 */
public record PortionUnit(
        @Id Long id,
        Long foodItemId,
        String unitName,
        Double grams,
        String source
) {

    @PersistenceCreator
    public PortionUnit {
    }

    /** Creates a new, unpersisted portion row. */
    public PortionUnit(Long foodItemId, String unitName, Double grams, String source) {
        this(null, foodItemId, unitName, grams, source);
    }
}
