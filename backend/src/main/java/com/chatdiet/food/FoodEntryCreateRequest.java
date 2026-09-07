package com.chatdiet.food;

/**
 * Body for {@code POST /api/food-entries} - the barcode quantity prompt's direct logging path,
 * which logs by {@code food_item} id (no alias round trip, immune to shared display names).
 *
 * @param foodItemId   the cached item to log against; must be active
 * @param grams        grams eaten
 * @param kcal         calories eaten, inverted to grams via the item's per-100g calories
 * @param servings     multiples of the item's typical serving
 * @param clientSentAt ISO-8601 instant the user submitted this, so an offline-queued entry
 *                     backdates to when it was actually eaten; null to log under now.
 *                     Exactly one of the three quantity fields must be a positive number.
 */
public record FoodEntryCreateRequest(Long foodItemId, Double grams, Double kcal, Double servings,
                                      String clientSentAt) {
}