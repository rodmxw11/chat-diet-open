package com.chatdiet.food;

import java.time.LocalDateTime;

/**
 * Request for the {@code log_food_by_upc} tool.
 *
 * @param upc              barcode identifying the product, looked up locally or via Open Food Facts
 * @param quantityServings number of typical servings eaten; provide this or {@code quantityG},
 *                         not both
 * @param quantityG        grams eaten; provide this or {@code quantityServings}, not both
 * @param loggedAt         when this was actually eaten, if the user mentioned a past day and/or
 *                         meal (e.g. "yesterday for dinner"); null to log under the current time
 */
public record LogFoodByUpcRequest(String upc, Double quantityServings, Double quantityG,
                                   LocalDateTime loggedAt) {
}
