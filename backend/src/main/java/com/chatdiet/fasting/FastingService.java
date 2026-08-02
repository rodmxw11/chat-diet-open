package com.chatdiet.fasting;

import com.chatdiet.day.DayBoundaryService;
import com.chatdiet.food.FoodEntryRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Computes fasting duration and eating-window information from logged food entries. */
@Service
public class FastingService {

    private final FoodEntryRepository foodEntryRepository;
    private final DayBoundaryService dayBoundaryService;

    public FastingService(FoodEntryRepository foodEntryRepository, DayBoundaryService dayBoundaryService) {
        this.foodEntryRepository = foodEntryRepository;
        this.dayBoundaryService = dayBoundaryService;
    }

    /** Time since the most recent logged food entry, i.e. the fast currently in progress. */
    public Optional<Duration> currentFastDuration() {
        return foodEntryRepository.findMostRecent()
                .map(entry -> Duration.between(entry.loggedAt(), LocalDateTime.now()));
    }

    /**
     * Returns the first and last food-logging timestamps within the given metabolic day, or
     * empty if nothing was logged that day.
     *
     * @param metabolicDate a date as defined by {@link DayBoundaryService}'s day-rollover rule
     */
    public Optional<EatingWindow> eatingWindowFor(LocalDate metabolicDate) {
        var start = dayBoundaryService.startOfMetabolicDay(metabolicDate);
        var end = dayBoundaryService.endOfMetabolicDay(metabolicDate);
        List<com.chatdiet.food.FoodEntry> entries = foodEntryRepository.findByLoggedAtBetween(start, end);
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new EatingWindow(entries.get(0).loggedAt(), entries.get(entries.size() - 1).loggedAt()));
    }

    /** The first and last time food was logged within a given day. */
    public record EatingWindow(LocalDateTime firstAte, LocalDateTime lastAte) {
    }
}
