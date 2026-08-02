package com.chatdiet.nutrition;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;

import java.time.LocalDate;

/**
 * The calorie target computed and cached for a single metabolic day, produced by
 * {@link AdaptiveTargetService} and persisted so the same target isn't recomputed once set.
 *
 * @param targetDate     the metabolic day (not calendar day) this target applies to
 * @param effectiveTdee  the TDEE actually used to derive {@code targetCalories}, after adaptive
 *                        adjustment based on observed weight change - not the raw Mifflin-St Jeor BMR
 * @param weightLbsUsed  the most recent logged weight at computation time, kept for traceability
 */
public record DailyTarget(
        @Id Long id,
        LocalDate targetDate,
        Integer targetCalories,
        Double effectiveTdee,
        Double weightLbsUsed
) {

    @PersistenceCreator
    public DailyTarget {
    }

    /** Creates a new, unpersisted target row. */
    public DailyTarget(LocalDate targetDate, Integer targetCalories, Double effectiveTdee, Double weightLbsUsed) {
        this(null, targetDate, targetCalories, effectiveTdee, weightLbsUsed);
    }
}
