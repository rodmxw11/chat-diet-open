package com.chatdiet.food;

import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Request for the {@code log_food_by_upc} tool.
 *
 * @param upc              barcode identifying the product, looked up locally or via Open Food Facts
 * @param quantityServings number of typical servings eaten
 * @param quantityG        grams eaten
 * @param quantityKcal     calories eaten, inverted to grams via the product's per-100g calories;
 *                         exactly one of the three quantity fields should be provided
 * @param loggedAt         when this was actually eaten, if the user mentioned a past day and/or
 *                         meal (e.g. "yesterday for dinner"); null to log under the current time
 */
public record LogFoodByUpcRequest(String upc, Double quantityServings, Double quantityG,
                                   Double quantityKcal,
                                   @ToolParam(required = false, description = "ISO-8601 local time the food was eaten, e.g. 2026-09-27T18:00; a date alone (2026-09-27) logs at noon. Omit to log under now.")
                                   String loggedAt) {
}