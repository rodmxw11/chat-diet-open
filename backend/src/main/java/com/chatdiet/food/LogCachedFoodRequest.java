package com.chatdiet.food;

import java.time.LocalDateTime;

/**
 * Request for the {@code log_cached_food} tool.
 *
 * @param foodName         name used to look up a previously-cached food item by best match
 * @param quantityServings number of typical servings eaten; provide this or {@code quantityG},
 *                         not both
 * @param quantityG        grams eaten; provide this or {@code quantityServings}, not both
 * @param loggedAt         when this was actually eaten, if the user mentioned a past day and/or
 *                         meal (e.g. "yesterday for dinner"); null to log under the current time
 */
public record LogCachedFoodRequest(String foodName, Double quantityServings, Double quantityG,
                                    LocalDateTime loggedAt) {
}
