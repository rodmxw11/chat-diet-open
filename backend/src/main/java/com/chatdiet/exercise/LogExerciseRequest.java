package com.chatdiet.exercise;

/**
 * Request DTO for {@link LogExerciseTool}.
 *
 * @param caloriesBurned optional calorie burn estimate; may be {@code null}
 */
public record LogExerciseRequest(String exerciseName, int durationMinutes, Integer caloriesBurned) {
}
