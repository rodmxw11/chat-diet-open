package com.chatdiet.nutrition;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.time.LocalDate;
import java.util.Optional;

/** Spring Data JDBC repository for {@link DailyTarget} rows. */
public interface DailyTargetRepository extends ListCrudRepository<DailyTarget, Long> {

    /** Looks up the target explicitly set for a specific metabolic day, if any. */
    @Query("SELECT * FROM daily_target WHERE target_date = :targetDate")
    Optional<DailyTarget> findByTargetDate(LocalDate targetDate);

    /**
     * Returns the most recently effective target on or before the given date - a goal applies
     * from the date it's set until superseded by a later one.
     */
    @Query("SELECT * FROM daily_target WHERE target_date <= :date ORDER BY target_date DESC LIMIT 1")
    Optional<DailyTarget> findMostRecentOnOrBefore(LocalDate date);
}
