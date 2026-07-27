package com.chatdiet.exercise;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ExerciseEntryRepository extends ListCrudRepository<ExerciseEntry, Long> {

    @Query("SELECT * FROM exercise_entry WHERE logged_at >= :start AND logged_at < :end")
    List<ExerciseEntry> findByLoggedAtBetween(LocalDateTime start, LocalDateTime end);
}
