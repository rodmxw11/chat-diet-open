package com.chatdiet.food;

/**
 * Response for {@code POST /api/food-entries}.
 *
 * @param reply the same fixed confirmation the chat pipeline echoes ("Logged \"X\" (150g): ...");
 *              also persisted into the day's transcript as the assistant half of the exchange
 * @param entry the saved row
 */
public record FoodEntryCreateResponse(String reply, FoodEntry entry) {
}