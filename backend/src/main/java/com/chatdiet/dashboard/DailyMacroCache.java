package com.chatdiet.dashboard;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Cached per-metabolic-day macro/calorie totals, summed from {@link com.chatdiet.food.FoodEntry}
 * by {@link DailyMacroCacheService}. Lets the macro chart read pre-aggregated totals instead of
 * re-summing every food entry in range on every request - most valuable for the 30-day view.
 */
public record DailyMacroCache(
        @Id Long id,
        LocalDate metabolicDate,
        int totalCalories,
        double proteinG,
        double carbsG,
        double fatG,
        LocalDateTime updatedAt
) {

    @PersistenceCreator
    public DailyMacroCache {
    }
}
