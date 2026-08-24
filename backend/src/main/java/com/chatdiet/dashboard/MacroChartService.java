package com.chatdiet.dashboard;

import com.chatdiet.day.DayBoundaryService;
import com.chatdiet.food.FoodEntryRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the per-day macro breakdown for the macro bar chart, bucketed by metabolic day the same
 * way {@link com.chatdiet.chart.ChartService} buckets its DAY-granularity series.
 */
@Service
public class MacroChartService {

    private final FoodEntryRepository foodEntryRepository;
    private final DayBoundaryService dayBoundaryService;

    public MacroChartService(FoodEntryRepository foodEntryRepository, DayBoundaryService dayBoundaryService) {
        this.foodEntryRepository = foodEntryRepository;
        this.dayBoundaryService = dayBoundaryService;
    }

    /** Returns one {@link DailyMacros} per metabolic day in {@code [from, to]}, oldest first. */
    public List<DailyMacros> dailyMacros(LocalDate from, LocalDate to) {
        var result = new ArrayList<DailyMacros>();
        for (var date = from; !date.isAfter(to); date = date.plusDays(1)) {
            var start = dayBoundaryService.startOfMetabolicDay(date);
            var end = dayBoundaryService.endOfMetabolicDay(date);
            var entries = foodEntryRepository.findByLoggedAtBetween(start, end);

            double protein = entries.stream().mapToDouble(e -> e.totalProteinG() != null ? e.totalProteinG() : 0).sum();
            double carbs = entries.stream().mapToDouble(e -> e.totalCarbsG() != null ? e.totalCarbsG() : 0).sum();
            double fat = entries.stream().mapToDouble(e -> e.totalFatG() != null ? e.totalFatG() : 0).sum();
            int calories = entries.stream().mapToInt(e -> e.totalCalories() != null ? e.totalCalories() : 0).sum();

            result.add(new DailyMacros(date, protein, carbs, fat, calories));
        }
        return result;
    }
}
