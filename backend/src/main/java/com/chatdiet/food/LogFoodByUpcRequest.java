package com.chatdiet.food;

/**
 * Request for the {@code log_food_by_upc} tool.
 *
 * @param upc              barcode identifying the product, looked up locally or via Open Food Facts
 * @param quantityServings number of typical servings eaten; provide this or {@code quantityG},
 *                         not both
 * @param quantityG        grams eaten; provide this or {@code quantityServings}, not both
 */
public record LogFoodByUpcRequest(String upc, Double quantityServings, Double quantityG) {
}
