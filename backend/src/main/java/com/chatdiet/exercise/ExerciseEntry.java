package com.chatdiet.exercise;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;

import java.time.LocalDateTime;

/**
 * Spring Data JDBC entity for a logged exercise session.
 *
 * @param caloriesBurned optional estimate of calories burned; may be {@code null}
 */
public record ExerciseEntry(
        @Id Long id,
        LocalDateTime loggedAt,
        String exerciseName,
        Integer durationMinutes,
        Integer caloriesBurned
) {

    @PersistenceCreator
    public ExerciseEntry {
    }

    public ExerciseEntry(LocalDateTime loggedAt, String exerciseName, Integer durationMinutes, Integer caloriesBurned) {
        this(null, loggedAt, exerciseName, durationMinutes, caloriesBurned);
    }
}
