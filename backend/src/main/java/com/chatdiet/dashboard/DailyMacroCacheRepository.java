package com.chatdiet.dashboard;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Spring Data JDBC repository for {@link DailyMacroCache} rows. */
public interface DailyMacroCacheRepository extends ListCrudRepository<DailyMacroCache, Long> {

    /** Looks up the cached totals for a specific metabolic day, if computed yet. */
    @Query("SELECT * FROM daily_macro_cache WHERE metabolic_date = :metabolicDate")
    Optional<DailyMacroCache> findByMetabolicDate(LocalDate metabolicDate);

    /** Returns every cached day in {@code [from, to]}, oldest first. Days with no entry are just absent. */
    @Query("SELECT * FROM daily_macro_cache WHERE metabolic_date >= :from AND metabolic_date <= :to ORDER BY metabolic_date")
    List<DailyMacroCache> findByMetabolicDateBetween(LocalDate from, LocalDate to);
}
