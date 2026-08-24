package com.chatdiet.nutrition;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;

import java.time.LocalDate;

/**
 * A manually-set daily calorie target, effective from {@code targetDate} until superseded by a
 * later one (carry-forward semantics via {@link DailyTargetRepository#findMostRecentOnOrBefore}).
 *
 * @param targetDate the metabolic day this target takes effect from
 */
public record DailyTarget(
        @Id Long id,
        LocalDate targetDate,
        Integer targetCalories
) {

    @PersistenceCreator
    public DailyTarget {
    }

    /** Creates a new, unpersisted target row. */
    public DailyTarget(LocalDate targetDate, Integer targetCalories) {
        this(null, targetDate, targetCalories);
    }
}
