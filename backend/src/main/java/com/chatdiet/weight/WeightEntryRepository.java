package com.chatdiet.weight;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;

public interface WeightEntryRepository extends ListCrudRepository<WeightEntry, Long> {

    @Query("SELECT * FROM weight_entry ORDER BY logged_at DESC LIMIT 1")
    Optional<WeightEntry> findMostRecent();

    @Query("SELECT * FROM weight_entry ORDER BY logged_at DESC LIMIT 2")
    List<WeightEntry> findTwoMostRecent();
}
