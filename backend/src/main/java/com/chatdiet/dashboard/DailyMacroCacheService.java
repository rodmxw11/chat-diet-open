package com.chatdiet.dashboard;

import com.chatdiet.day.DayBoundaryService;
import com.chatdiet.food.FoodEntryRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Keeps {@link DailyMacroCache} in sync with {@link com.chatdiet.food.FoodEntry}. Recomputes one
 * metabolic day at a time - cheap, since callers only ever recompute the day they just wrote to,
 * as opposed to the macro chart re-summing every food entry in its range on every read.
 */
@Service
public class DailyMacroCacheService {

    private final FoodEntryRepository foodEntryRepository;
    private final DailyMacroCacheRepository dailyMacroCacheRepository;
    private final DayBoundaryService dayBoundaryService;

    public DailyMacroCacheService(FoodEntryRepository foodEntryRepository,
                                   DailyMacroCacheRepository dailyMacroCacheRepository,
                                   DayBoundaryService dayBoundaryService) {
        this.foodEntryRepository = foodEntryRepository;
        this.dailyMacroCacheRepository = dailyMacroCacheRepository;
        this.dayBoundaryService = dayBoundaryService;
    }

    /** Re-sums every food entry on the given metabolic day and upserts its cache row. */
    public void recompute(LocalDate metabolicDate) {
        var start = dayBoundaryService.startOfMetabolicDay(metabolicDate);
        var end = dayBoundaryService.endOfMetabolicDay(metabolicDate);
        var entries = foodEntryRepository.findByLoggedAtBetween(start, end);

        double protein = entries.stream().mapToDouble(e -> e.totalProteinG() != null ? e.totalProteinG() : 0).sum();
        double carbs = entries.stream().mapToDouble(e -> e.totalCarbsG() != null ? e.totalCarbsG() : 0).sum();
        double fat = entries.stream().mapToDouble(e -> e.totalFatG() != null ? e.totalFatG() : 0).sum();
        int calories = entries.stream().mapToInt(e -> e.totalCalories() != null ? e.totalCalories() : 0).sum();

        var existingId = dailyMacroCacheRepository.findByMetabolicDate(metabolicDate)
                .map(DailyMacroCache::id)
                .orElse(null);
        dailyMacroCacheRepository.save(
                new DailyMacroCache(existingId, metabolicDate, calories, protein, carbs, fat, LocalDateTime.now()));
    }

    /** Recomputes the cache row for whichever metabolic day the given timestamp falls on. */
    public void recomputeForTimestamp(LocalDateTime loggedAt) {
        recompute(dayBoundaryService.metabolicDateOf(loggedAt));
    }

    /**
     * Backfills cache rows for every metabolic day spanned by existing food entries, so history
     * logged before this cache existed shows up immediately rather than waiting for a new write to
     * each of those days. Cheap and idempotent enough to just always run at startup - personal-app
     * data volumes make re-summing the full history once, on boot, a non-issue.
     */
    @PostConstruct
    void backfill() {
        var oldest = foodEntryRepository.findOldest();
        var newest = foodEntryRepository.findMostRecent();
        if (oldest.isEmpty() || newest.isEmpty()) return;

        var from = dayBoundaryService.metabolicDateOf(oldest.get().loggedAt());
        var to = dayBoundaryService.metabolicDateOf(newest.get().loggedAt());
        for (var date = from; !date.isAfter(to); date = date.plusDays(1)) {
            recompute(date);
        }
    }
}
