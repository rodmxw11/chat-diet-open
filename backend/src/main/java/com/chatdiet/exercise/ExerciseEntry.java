package com.chatdiet.exercise;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;

import java.time.LocalDateTime;

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
