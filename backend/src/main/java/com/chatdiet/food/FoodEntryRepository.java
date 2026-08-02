package com.chatdiet.food;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Spring Data JDBC repository for {@link FoodEntry} records. */
public interface FoodEntryRepository extends ListCrudRepository<FoodEntry, Long> {

    /** Returns the most recently logged food entry, if any, used as the target for corrections. */
    @Query("SELECT * FROM food_entry ORDER BY logged_at DESC LIMIT 1")
    Optional<FoodEntry> findMostRecent();

    /** Returns all entries logged within {@code [start, end)}, ordered chronologically. */
    @Query("SELECT * FROM food_entry WHERE logged_at >= :start AND logged_at < :end ORDER BY logged_at")
    List<FoodEntry> findByLoggedAtBetween(LocalDateTime start, LocalDateTime end);
}
