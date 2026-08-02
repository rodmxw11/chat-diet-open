package com.chatdiet.food;

/**
 * Request for the {@code log_cached_food} tool.
 *
 * @param foodName         name used to look up a previously-cached food item by best match
 * @param quantityServings number of typical servings eaten; provide this or {@code quantityG},
 *                         not both
 * @param quantityG        grams eaten; provide this or {@code quantityServings}, not both
 */
public record LogCachedFoodRequest(String foodName, Double quantityServings, Double quantityG) {
}
