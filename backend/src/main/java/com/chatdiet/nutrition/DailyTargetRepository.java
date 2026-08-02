package com.chatdiet.nutrition;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.time.LocalDate;
import java.util.Optional;

/** Spring Data JDBC repository for cached {@link DailyTarget} rows. */
public interface DailyTargetRepository extends ListCrudRepository<DailyTarget, Long> {

    /** Looks up the cached target for a specific metabolic day, if already computed. */
    @Query("SELECT * FROM daily_target WHERE target_date = :targetDate")
    Optional<DailyTarget> findByTargetDate(LocalDate targetDate);

    /** Returns the most recently dated target, used as the baseline effective TDEE for new computations. */
    @Query("SELECT * FROM daily_target ORDER BY target_date DESC LIMIT 1")
    Optional<DailyTarget> findMostRecent();
}
