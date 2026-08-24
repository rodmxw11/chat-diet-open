package com.chatdiet.nutrition;

import java.time.LocalDate;

/**
 * Request for the {@code set_calorie_goal} tool.
 *
 * @param effectiveFrom the metabolic day the goal takes effect from; defaults to today when null
 */
public record SetCalorieGoalRequest(Integer targetCalories, LocalDate effectiveFrom) {
}
