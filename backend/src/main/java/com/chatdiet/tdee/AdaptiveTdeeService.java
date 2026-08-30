package com.chatdiet.tdee;

import com.chatdiet.dashboard.DailyMacroCache;
import com.chatdiet.dashboard.DailyMacroCacheRepository;
import com.chatdiet.dashboard.WeightTrendService;
import com.chatdiet.day.DayBoundaryService;
import com.chatdiet.nutrition.DailyTargetRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Back-calculates total daily energy expenditure from logged calorie intake and the smoothed
 * weight trend, instead of a generic BMR formula: over a window,
 * {@code caloriesIn - caloriesOut = ΔweightLbs × 3500}, so
 * {@code TDEE ≈ avgDailyIntake - (slopeLbsPerDay × 3500)}, where the slope comes from an OLS fit
 * of the window's smoothed trend values, not a two-point endpoint difference - fitting a line
 * through every point uses the whole window's information instead of throwing away every day but
 * the first and last, which is also the highest-variance way to estimate a slope for a given
 * window width.
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

    /** Below this many real trend points, an OLS slope is too underdetermined to trust. */
    private static final int MIN_TREND_POINTS = 7;

    /**
     * How far back to look for a calorie-goal change before caveating the estimate: the first
     * ~2-3 weeks of a new deficit/surplus are dominated by glycogen and water shifts, not fat
     * mass, so 3500 kcal/lb doesn't hold yet. A goal change is an imperfect but simple, data-
     * grounded proxy for "started a new diet phase" - not the only way someone starts one, but
     * the only signal already in the schema.
     */
    private static final int PHASE_CAVEAT_LOOKBACK_DAYS = 21;

    private static final double CALORIES_PER_POUND = 3500.0;

    private final WeightTrendService weightTrendService;
    private final DailyMacroCacheRepository dailyMacroCacheRepository;
    private final DailyTargetRepository dailyTargetRepository;
    private final DayBoundaryService dayBoundaryService;

    public AdaptiveTdeeService(WeightTrendService weightTrendService,
                                DailyMacroCacheRepository dailyMacroCacheRepository,
                                DailyTargetRepository dailyTargetRepository,
                                DayBoundaryService dayBoundaryService) {
        this.weightTrendService = weightTrendService;
        this.dailyMacroCacheRepository = dailyMacroCacheRepository;
        this.dailyTargetRepository = dailyTargetRepository;
        this.dayBoundaryService = dayBoundaryService;
    }

    public TdeeResult estimate() {
        var to = dayBoundaryService.today();
        var from = to.minusDays(WINDOW_DAYS);
        var trendByDate = weightTrendService.smoothedTrendByDate();

        var points = trendPointsInWindow(trendByDate, from, to);
        if (points.size() < MIN_TREND_POINTS) {
            return new TdeeResult.Unavailable(
                    "only " + points.size() + " days of weight-trend data in the last " + WINDOW_DAYS
                            + " days - need at least " + MIN_TREND_POINTS);
        }

        var loggedDays = dailyMacroCacheRepository.findByMetabolicDateBetween(from.plusDays(1), to);
        if (loggedDays.size() < MIN_LOGGED_DAYS) {
            return new TdeeResult.Unavailable(
                    "only " + loggedDays.size() + " of the last " + WINDOW_DAYS
                            + " days have logged food - need at least " + MIN_LOGGED_DAYS);
        }

        double avgDailyIntake = loggedDays.stream().mapToInt(DailyMacroCache::totalCalories).average().orElseThrow();
        var regression = fitLine(points);
        double weightChangeLbs = regression.slopePerDay() * WINDOW_DAYS;
        double avgDailyDeficitFromWeight = regression.slopePerDay() * CALORIES_PER_POUND;
        int estimatedCalories = (int) Math.round(avgDailyIntake - avgDailyDeficitFromWeight);
        int standardErrorCalories = (int) Math.round(regression.standardErrorPerDay() * CALORIES_PER_POUND);

        return new TdeeResult.Estimate(estimatedCalories, standardErrorCalories, WINDOW_DAYS, loggedDays.size(),
                weightChangeLbs, phaseCaveat(from));
    }

    /**
     * Only actual (real-or-interpolated) trend entries within the window - deliberately excludes
     * any date that would only resolve via flat carry-forward past the last real weigh-in, so a
     * stale trend doesn't fake extra data points with zero real information in them.
     */
    private static List<double[]> trendPointsInWindow(Map<LocalDate, Double> trendByDate, LocalDate from,
                                                        LocalDate to) {
        var points = new ArrayList<double[]>();
        for (var date = from; !date.isAfter(to); date = date.plusDays(1)) {
            var value = trendByDate.get(date);
            if (value != null) {
                points.add(new double[]{ChronoUnit.DAYS.between(from, date), value});
            }
        }
        return points;
    }

    /** Ordinary least squares slope (per day) and its standard error, over {@code (dayOffset, trendValue)} pairs. */
    private static Regression fitLine(List<double[]> points) {
        int n = points.size();
        double sumX = 0, sumY = 0;
        for (var p : points) {
            sumX += p[0];
            sumY += p[1];
        }
        double meanX = sumX / n, meanY = sumY / n;

        double sxx = 0, sxy = 0;
        for (var p : points) {
            double dx = p[0] - meanX;
            sxx += dx * dx;
            sxy += dx * (p[1] - meanY);
        }
        double slope = sxy / sxx;
        double intercept = meanY - slope * meanX;

        double sse = 0;
        for (var p : points) {
            double residual = p[1] - (intercept + slope * p[0]);
            sse += residual * residual;
        }
        double standardError = Math.sqrt((sse / (n - 2)) / sxx);

        return new Regression(slope, standardError);
    }

    private record Regression(double slopePerDay, double standardErrorPerDay) {
    }

    private String phaseCaveat(LocalDate windowStart) {
        var cutoff = windowStart.minusDays(PHASE_CAVEAT_LOOKBACK_DAYS);
        return dailyTargetRepository.findMostRecentOnOrAfter(cutoff)
                .map(target -> "your calorie target changed on " + target.targetDate() + " - if that started a "
                        + "new diet phase, this estimate is likely skewed by water/glycogen shifts for the first "
                        + "few weeks, not real fat-mass change")
                .orElse(null);
    }
}
