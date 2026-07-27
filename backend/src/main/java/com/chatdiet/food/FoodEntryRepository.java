package com.chatdiet.food;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.util.Optional;

public interface FoodEntryRepository extends ListCrudRepository<FoodEntry, Long> {

    @Query("SELECT * FROM food_entry ORDER BY logged_at DESC LIMIT 1")
    Optional<FoodEntry> findMostRecent();
}
