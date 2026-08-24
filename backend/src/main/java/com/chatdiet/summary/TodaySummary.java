package com.chatdiet.summary;

import java.time.LocalDate;

/**
 * Response for {@code GET /api/summary/today}, polled by the header.
 *
 * @param targetCalories    null if no goal has ever been set
 * @param remainingCalories null if no goal has ever been set
 */
public record TodaySummary(LocalDate metabolicDate, Integer targetCalories, int consumedCalories,
                            Integer remainingCalories, int entryCount) {
}
