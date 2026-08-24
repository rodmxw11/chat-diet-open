package com.chatdiet.dashboard;

import com.chatdiet.day.DayBoundaryService;
import com.chatdiet.nutrition.NutritionService;
import com.chatdiet.weight.WeightEntryRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes the 30-day weight trend: actual weigh-ins, a classic Hacker's Diet exponentially
 * smoothed trend line, and a straight-line goal trajectory.
 */
@Service
public class WeightTrendService {

    /** Standard Hacker's Diet smoothing constant. */
    private static final double SMOOTHING_ALPHA = 0.1;

    private static final int WINDOW_DAYS = 30;

    private final WeightEntryRepository weightEntryRepository;
    private final NutritionService nutritionService;
    private final DayBoundaryService dayBoundaryService;

    public WeightTrendService(WeightEntryRepository weightEntryRepository, NutritionService nutritionService,
                               DayBoundaryService dayBoundaryService) {
        this.weightEntryRepository = weightEntryRepository;
        this.nutritionService = nutritionService;
        this.dayBoundaryService = dayBoundaryService;
    }

    public WeightTrendResponse trend30Day() {
        var to = dayBoundaryService.today();
        var from = to.minusDays(WINDOW_DAYS - 1);

        var byDay = averagedWeighInsByDay(from, to);

        var actual = new ArrayList<WeighIn>();
        var smoothed = new ArrayList<TrendPoint>();
        Double runningTrend = null;
        for (var date = from; !date.isAfter(to); date = date.plusDays(1)) {
            var todaysWeight = byDay.get(date);
            if (todaysWeight != null) {
                actual.add(new WeighIn(date, todaysWeight));
                runningTrend = runningTrend == null
                        ? todaysWeight
                        : runningTrend + (todaysWeight - runningTrend) * SMOOTHING_ALPHA;
            }
            // No trend point at all until the first weigh-in in the window; flat carry-forward thereafter.
            if (runningTrend != null) {
                smoothed.add(new TrendPoint(date, runningTrend));
            }
        }

        GoalLine goal = actual.isEmpty() ? null
                : new GoalLine(actual.get(0).date(), actual.get(0).weightLbs(), nutritionService.weeklyRateLbs() / 7.0);

        return new WeightTrendResponse(actual, smoothed, goal);
    }

    /** Averages same-metabolic-day readings into one value per day, matching ChartService's WEIGHT metric. */
    private Map<LocalDate, Double> averagedWeighInsByDay(LocalDate from, LocalDate to) {
        var start = dayBoundaryService.startOfMetabolicDay(from);
        var end = dayBoundaryService.endOfMetabolicDay(to);
        var entries = weightEntryRepository.findByLoggedAtBetween(start, end);

        var sums = new LinkedHashMap<LocalDate, double[]>(); // [sum, count]
        for (var entry : entries) {
            if (entry.weightLbs() == null) {
                continue;
            }
            var date = dayBoundaryService.metabolicDateOf(entry.loggedAt());
            var bucket = sums.computeIfAbsent(date, d -> new double[2]);
            bucket[0] += entry.weightLbs();
            bucket[1] += 1;
        }

        var result = new LinkedHashMap<LocalDate, Double>();
        sums.forEach((date, bucket) -> result.put(date, bucket[0] / bucket[1]));
        return result;
    }
}
