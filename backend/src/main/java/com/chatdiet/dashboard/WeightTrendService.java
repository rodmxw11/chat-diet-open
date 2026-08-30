package com.chatdiet.dashboard;

import com.chatdiet.day.DayBoundaryService;
import com.chatdiet.nutrition.NutritionService;
import com.chatdiet.weight.WeightEntry;
import com.chatdiet.weight.WeightEntryRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes the 30-day weight trend: actual weigh-ins, an exponentially smoothed trend line
 * matching <a href="https://trendweight.com/math">TrendWeight's</a> algorithm, and a straight-line
 * goal trajectory.
 *
 * <p>The trend is computed over the user's <em>entire</em> weigh-in history, not just the 30-day
 * display window, with missing days linearly interpolated first - both per TrendWeight's approach.
 * Seeding the smoothing fresh at the window boundary (the previous implementation) would distort
 * the trend for weeks after any window shift, since alpha=0.1 takes many data points to converge;
 * and updating the trend only on days with a real weigh-in makes it jump sharply across a gap
 * instead of easing across it. Only the window is returned to the frontend.
 */
@Service
public class WeightTrendService {

    /** TrendWeight's smoothing constant: each day blends 90% prior trend, 10% today's reading. */
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

    /**
     * The full smoothed trend line (Hacker's Diet EWMA) across all weigh-in history, keyed by
     * date - shared by the 30-day chart and anything else (e.g. adaptive TDEE) that needs the
     * underlying trend rather than just the display window. Empty map if there's no weigh-in
     * history at all.
     */
    public Map<LocalDate, Double> smoothedTrendByDate() {
        var byDay = earliestWeighInByDay(weightEntryRepository.findAll());
        if (byDay.isEmpty()) {
            return Map.of();
        }
        return smooth(interpolateGaps(byDay));
    }

    public WeightTrendResponse trend30Day() {
        var to = dayBoundaryService.today();
        var from = to.minusDays(WINDOW_DAYS - 1);

        var byDay = earliestWeighInByDay(weightEntryRepository.findAll());
        if (byDay.isEmpty()) {
            return new WeightTrendResponse(List.of(), List.of(), null);
        }

        var trendByDate = smoothedTrendByDate();

        var actual = new ArrayList<WeighIn>();
        for (var date = from; !date.isAfter(to); date = date.plusDays(1)) {
            var weight = byDay.get(date);
            if (weight != null) {
                actual.add(new WeighIn(date, weight));
            }
        }

        var smoothed = new ArrayList<TrendPoint>();
        Double lastKnownTrend = null;
        for (var date = from; !date.isAfter(to); date = date.plusDays(1)) {
            var trend = trendByDate.get(date);
            if (trend != null) {
                lastKnownTrend = trend;
            }
            // Flat carry-forward past the last real-or-interpolated day (e.g. no weigh-in since);
            // nothing at all before the very first weigh-in of all time.
            if (lastKnownTrend != null) {
                smoothed.add(new TrendPoint(date, lastKnownTrend));
            }
        }

        GoalLine goal = actual.isEmpty() ? null
                : new GoalLine(actual.get(0).date(), actual.get(0).weightLbs(), nutritionService.weeklyRateLbs() / 7.0);

        return new WeightTrendResponse(actual, smoothed, goal);
    }

    /**
     * One reading per metabolic day across all history: the earliest logged reading of the day
     * wins, matching TrendWeight's same-day dedup (it prefers a manually-tagged entry first, but
     * this app has no such distinction - every entry is manual).
     */
    private Map<LocalDate, Double> earliestWeighInByDay(List<WeightEntry> entries) {
        var earliestTimestamp = new LinkedHashMap<LocalDate, LocalDateTime>();
        var result = new LinkedHashMap<LocalDate, Double>();
        for (var entry : entries) {
            if (entry.weightLbs() == null) {
                continue;
            }
            var date = dayBoundaryService.metabolicDateOf(entry.loggedAt());
            var existing = earliestTimestamp.get(date);
            if (existing == null || entry.loggedAt().isBefore(existing)) {
                earliestTimestamp.put(date, entry.loggedAt());
                result.put(date, entry.weightLbs());
            }
        }
        return result;
    }

    /**
     * Fills gaps between real weigh-ins with linearly interpolated daily values, so a multi-day
     * gap eases the smoothed trend across it one day at a time instead of jumping straight from
     * the day before the gap to the day after it.
     */
    private List<Map.Entry<LocalDate, Double>> interpolateGaps(Map<LocalDate, Double> byDay) {
        var dates = new ArrayList<>(byDay.keySet());
        dates.sort(LocalDate::compareTo);

        var dense = new ArrayList<Map.Entry<LocalDate, Double>>();
        LocalDate previousDate = null;
        double previousWeight = 0;
        for (var date : dates) {
            double weight = byDay.get(date);
            if (previousDate != null) {
                long daysBetween = ChronoUnit.DAYS.between(previousDate, date);
                if (daysBetween > 1) {
                    double changePerDay = (weight - previousWeight) / daysBetween;
                    double interpolated = previousWeight;
                    for (var gapDate = previousDate.plusDays(1); gapDate.isBefore(date); gapDate = gapDate.plusDays(1)) {
                        interpolated += changePerDay;
                        dense.add(Map.entry(gapDate, interpolated));
                    }
                }
            }
            dense.add(Map.entry(date, weight));
            previousDate = date;
            previousWeight = weight;
        }
        return dense;
    }

    /** Exponentially smooths the (gap-filled) daily sequence, seeded by the very first value. */
    private Map<LocalDate, Double> smooth(List<Map.Entry<LocalDate, Double>> dense) {
        var trendByDate = new LinkedHashMap<LocalDate, Double>();
        Double trend = null;
        for (var point : dense) {
            trend = trend == null ? point.getValue() : trend + SMOOTHING_ALPHA * (point.getValue() - trend);
            trendByDate.put(point.getKey(), trend);
        }
        return trendByDate;
    }
}
