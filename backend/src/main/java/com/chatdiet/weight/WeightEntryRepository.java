package com.chatdiet.weight;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Spring Data JDBC repository for {@link WeightEntry} rows. */
public interface WeightEntryRepository extends ListCrudRepository<WeightEntry, Long> {

    /** The single latest weigh-in, used as the correction target and as the current weight. */
    @Query("SELECT * FROM weight_entry ORDER BY logged_at DESC LIMIT 1")
    Optional<WeightEntry> findMostRecent();

    /** Entries logged in [start, end), ordered chronologically. */
    @Query("SELECT * FROM weight_entry WHERE logged_at >= :start AND logged_at < :end ORDER BY logged_at")
    List<WeightEntry> findByLoggedAtBetween(LocalDateTime start, LocalDateTime end);
}
