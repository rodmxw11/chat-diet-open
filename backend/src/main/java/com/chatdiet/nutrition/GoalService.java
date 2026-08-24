package com.chatdiet.nutrition;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Manages the user's manually-set calorie goal. A goal set with a given {@code effectiveFrom}
 * date applies from that date forward until a later goal supersedes it (carry-forward
 * semantics) - replacing the old TDEE-based adaptive target.
 */
@Service
public class GoalService {

    private final DailyTargetRepository dailyTargetRepository;

    public GoalService(DailyTargetRepository dailyTargetRepository) {
        this.dailyTargetRepository = dailyTargetRepository;
    }

    /**
     * Sets (or replaces) the calorie target effective from the given date. Upserts in place if a
     * target already exists for that exact date, so repeatedly adjusting "today's" goal doesn't
     * accumulate rows.
     */
    public DailyTarget setGoal(LocalDate effectiveFrom, int targetCalories) {
        var existing = dailyTargetRepository.findByTargetDate(effectiveFrom).orElse(null);
        var target = existing != null
                ? new DailyTarget(existing.id(), effectiveFrom, targetCalories)
                : new DailyTarget(effectiveFrom, targetCalories);
        return dailyTargetRepository.save(target);
    }

    /**
     * Returns the calorie target in effect for the given metabolic date - the most recently set
     * goal on or before that date - or empty if no goal has ever been set.
     */
    public Optional<DailyTarget> targetFor(LocalDate date) {
        return dailyTargetRepository.findMostRecentOnOrBefore(date);
    }
}
