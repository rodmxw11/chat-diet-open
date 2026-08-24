package com.chatdiet.fooditem;

/**
 * One row of the food-item shopping-list picker rendered inline in a chat response.
 *
 * @param typicalServingG the item's typical serving size in grams, if known; shown for context only
 */
public record FoodItemOption(Long id, String name, Double typicalServingG) {
}
