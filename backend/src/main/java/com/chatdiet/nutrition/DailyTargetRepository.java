package com.chatdiet.nutrition;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyTargetRepository extends ListCrudRepository<DailyTarget, Long> {

    @Query("SELECT * FROM daily_target WHERE target_date = :targetDate")
    Optional<DailyTarget> findByTargetDate(LocalDate targetDate);

    @Query("SELECT * FROM daily_target ORDER BY target_date DESC LIMIT 1")
    Optional<DailyTarget> findMostRecent();
}
