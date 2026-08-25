package com.chatdiet.food;

import java.time.LocalDate;

/**
 * Parameters for {@code list_food_entries}.
 *
 * @param date the metabolic day to list, resolved by the model itself from phrasing like
 *             "today"/"yesterday"/an explicit date, anchored to the current date/time it's given
 */
public record ListFoodEntriesRequest(LocalDate date) {
}
