package com.chatdiet.tdee;

import com.chatdiet.dashboard.DailyMacroCache;
import com.chatdiet.dashboard.DailyMacroCacheRepository;
import com.chatdiet.dashboard.WeightTrendService;
import com.chatdiet.day.DayBoundaryService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;

/**
 * Back-calculates total daily energy expenditure from logged calorie intake and the smoothed
 * weight trend, instead of a generic BMR formula: over a window,
 * {@code caloriesIn - caloriesOut = ΔweightLbs × 3500}, so
 * {@code TDEE ≈ avgDailyIntake - (ΔweightLbs × 3500 / windowDays)}.
 *
 * <p>Using the already-smoothed trend (not raw weigh-ins) means a single noisy reading can't
 * swing the estimate, and any systematic bias in logged-calorie estimates gets absorbed into the
 * computed TDEE rather than corrupting a deficit calculation. This is read-only - nothing here
 * feeds back into {@code DailyTarget} automatically. An earlier version of this idea did that and
 * was removed for oscillating on sparse weigh-ins without damping (see SPEC.md); this avoids that
 * failure mode by simply not closing the loop.
 */
@Service
public class AdaptiveTdeeService {

    private static final int WINDOW_DAYS = 14;

    /** At least half the window must have food logged, or the average intake isn't trustworthy. */
    private static final int MIN_LOGGED_DAYS = 7;

    private static final double CALORIES_PER_POUND = 3500.0;

    private final WeightTrendService weightTrendService;
    private final DailyMacroCacheRepository dailyMacroCacheRepository;
    private final DayBoundaryService dayBoundaryService;

    public AdaptiveTdeeService(WeightTrendService weightTrendService,
                                DailyMacroCacheRepository dailyMacroCacheRepository,
                                DayBoundaryService dayBoundaryService) {
        this.weightTrendService = weightTrendService;
        this.dailyMacroCacheRepository = dailyMacroCacheRepository;
        this.dayBoundaryService = dayBoundaryService;
    }

    public TdeeResult estimate() {
        var to = dayBoundaryService.today();
        var from = to.minusDays(WINDOW_DAYS);
        var trendByDate = weightTrendService.smoothedTrendByDate();

        var trendAtFrom = onOrBefore(trendByDate, from);
        var trendAtTo = onOrBefore(trendByDate, to);
        if (trendAtFrom == null || trendAtTo == null) {
            return new TdeeResult.Unavailable(
                    "not enough weigh-in history yet - need a weigh-in from around " + WINDOW_DAYS + " days ago");
        }

        var loggedDays = dailyMacroCacheRepository.findByMetabolicDateBetween(from.plusDays(1), to);
        if (loggedDays.size() < MIN_LOGGED_DAYS) {
            return new TdeeResult.Unavailable(
                    "only " + loggedDays.size() + " of the last " + WINDOW_DAYS
                            + " days have logged food - need at least " + MIN_LOGGED_DAYS);
        }

        double avgDailyIntake = loggedDays.stream().mapToInt(DailyMacroCache::totalCalories).average().orElseThrow();
        double weightChangeLbs = trendAtTo - trendAtFrom;
        double avgDailyDeficitFromWeight = weightChangeLbs * CALORIES_PER_POUND / WINDOW_DAYS;
        int estimatedCalories = (int) Math.round(avgDailyIntake - avgDailyDeficitFromWeight);

        return new TdeeResult.Estimate(estimatedCalories, WINDOW_DAYS, loggedDays.size(), weightChangeLbs);
    }

    /**
     * Walks backward from {@code date} to the nearest date with a trend value - mirrors the flat
     * carry-forward {@link WeightTrendService#trend30Day()} already does for display, generalized
     * to any date rather than just the fixed 30-day window.
     */
    private static Double onOrBefore(Map<LocalDate, Double> trendByDate, LocalDate date) {
        var earliest = trendByDate.keySet().stream().min(LocalDate::compareTo).orElse(null);
        if (earliest == null || date.isBefore(earliest)) {
            return null;
        }
        for (var d = date; !d.isBefore(earliest); d = d.minusDays(1)) {
            var value = trendByDate.get(d);
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
