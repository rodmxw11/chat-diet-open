package com.chatdiet.nutrition;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;

import java.time.LocalDate;

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

    public DailyTarget(LocalDate targetDate, Integer targetCalories, Double effectiveTdee, Double weightLbsUsed) {
        this(null, targetDate, targetCalories, effectiveTdee, weightLbsUsed);
    }
}
