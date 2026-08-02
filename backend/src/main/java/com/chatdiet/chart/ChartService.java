package com.chatdiet.chart;

import com.chatdiet.day.DayBoundaryService;
import com.chatdiet.exercise.ExerciseEntryRepository;
import com.chatdiet.food.FoodEntryRepository;
import com.chatdiet.nutrition.AdaptiveTargetService;
import com.chatdiet.shopping.PurchaseHistoryRepository;
import com.chatdiet.weight.WeightEntryRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Computes chart series from the underlying food/exercise/weight/purchase data for
 * {@link ShowChartTool}. Buckets the requested date range per the chosen {@link Granularity}
 * (respecting the app's metabolic-day boundary rather than calendar midnight), aggregates the
 * requested {@link ChartMetric} per bucket, and optionally converts to a running cumulative total.
 */
@Service
public class ChartService {

    private final FoodEntryRepository foodEntryRepository;
    private final ExerciseEntryRepository exerciseEntryRepository;
    private final WeightEntryRepository weightEntryRepository;
    private final PurchaseHistoryRepository purchaseHistoryRepository;
    private final AdaptiveTargetService adaptiveTargetService;
    private final DayBoundaryService dayBoundaryService;

    public ChartService(FoodEntryRepository foodEntryRepository, ExerciseEntryRepository exerciseEntryRepository,
                         WeightEntryRepository weightEntryRepository, PurchaseHistoryRepository purchaseHistoryRepository,
                         AdaptiveTargetService adaptiveTargetService, DayBoundaryService dayBoundaryService) {
        this.foodEntryRepository = foodEntryRepository;
        this.exerciseEntryRepository = exerciseEntryRepository;
        this.weightEntryRepository = weightEntryRepository;
        this.purchaseHistoryRepository = purchaseHistoryRepository;
        this.adaptiveTargetService = adaptiveTargetService;
        this.dayBoundaryService = dayBoundaryService;
    }

    /**
     * Builds the one or more series (e.g. MACROS yields three) for the requested metric and date
     * range, applying cumulative totals where requested or forced (hourly granularity).
     */
    public List<ChartSeries> compute(ChartRequest request) {
        var buckets = buildBuckets(request.from(), request.to(), request.granularity());
        // Intraday charts read as a step function per-hour; cumulative-against-target is the useful shape.
        var cumulative = request.cumulative() || request.granularity() == Granularity.HOUR;

        return switch (request.metric()) {
            case CALORIES -> List.of(seriesFor("Calories", buckets, cumulative,
                    b -> sumCalories(b.start(), b.end()),
                    request.includeGoal() ? this::sumTarget : null));
            case MACROS -> List.of(
                    seriesFor("Protein (g)", buckets, cumulative, b -> sumProtein(b.start(), b.end()), null),
                    seriesFor("Carbs (g)", buckets, cumulative, b -> sumCarbs(b.start(), b.end()), null),
                    seriesFor("Fat (g)", buckets, cumulative, b -> sumFat(b.start(), b.end()), null));
            case WEIGHT -> List.of(seriesFor("Weight (lbs)", buckets, false,
                    b -> averageWeight(b.start(), b.end()), null));
            case DEFICIT -> List.of(seriesFor("Deficit (kcal)", buckets, cumulative,
                    b -> sumTarget(b) - (sumCalories(b.start(), b.end()) - sumExerciseBurn(b.start(), b.end())),
                    request.includeGoal() ? b -> 0.0 : null));
            case COST -> List.of(seriesFor("Cost ($)", buckets, cumulative, this::sumCost, null));
        };
    }

    private ChartSeries seriesFor(String label, List<Bucket> buckets, boolean cumulative,
                                   java.util.function.ToDoubleFunction<Bucket> valueFn,
                                   java.util.function.ToDoubleFunction<Bucket> targetFn) {
        var points = new ArrayList<SeriesPoint>();
        double runningValue = 0;
        double runningTarget = 0;
        for (var bucket : buckets) {
            var rawValue = valueFn.applyAsDouble(bucket);
            runningValue += rawValue;
            var value = cumulative ? runningValue : rawValue;

            Double target = null;
            if (targetFn != null) {
                var rawTarget = targetFn.applyAsDouble(bucket);
                runningTarget += rawTarget;
                target = cumulative ? runningTarget : rawTarget;
            }

            points.add(new SeriesPoint(bucket.start().atZone(ZoneId.systemDefault()).toInstant(), value, target));
        }
        return new ChartSeries(label, points);
    }

    private double sumCalories(LocalDateTime start, LocalDateTime end) {
        return foodEntryRepository.findByLoggedAtBetween(start, end).stream()
                .mapToInt(e -> e.totalCalories() != null ? e.totalCalories() : 0)
                .sum();
    }

    private double sumProtein(LocalDateTime start, LocalDateTime end) {
        return foodEntryRepository.findByLoggedAtBetween(start, end).stream()
                .mapToDouble(e -> e.totalProteinG() != null ? e.totalProteinG() : 0)
                .sum();
    }

    private double sumCarbs(LocalDateTime start, LocalDateTime end) {
        return foodEntryRepository.findByLoggedAtBetween(start, end).stream()
                .mapToDouble(e -> e.totalCarbsG() != null ? e.totalCarbsG() : 0)
                .sum();
    }

    private double sumFat(LocalDateTime start, LocalDateTime end) {
        return foodEntryRepository.findByLoggedAtBetween(start, end).stream()
                .mapToDouble(e -> e.totalFatG() != null ? e.totalFatG() : 0)
                .sum();
    }

    private double sumExerciseBurn(LocalDateTime start, LocalDateTime end) {
        return exerciseEntryRepository.findByLoggedAtBetween(start, end).stream()
                .mapToInt(e -> e.caloriesBurned() != null ? e.caloriesBurned() : 0)
                .sum();
    }

    private double sumCost(Bucket bucket) {
        return purchaseHistoryRepository.findByPurchasedAtBetween(bucket.start(), bucket.end()).stream()
                .mapToDouble(p -> p.costUsd() != null ? p.costUsd() : 0)
                .sum();
    }

    private double averageWeight(LocalDateTime start, LocalDateTime end) {
        return weightEntryRepository.findByLoggedAtBetween(start, end).stream()
                .mapToDouble(e -> e.weightLbs() != null ? e.weightLbs() : 0)
                .average()
                .orElse(0);
    }

    /** Sum of each covered metabolic date's calorie target - matches how the actual value is summed per bucket. */
    private double sumTarget(Bucket bucket) {
        return bucket.metabolicDates().stream()
                .mapToDouble(date -> adaptiveTargetService.getOrComputeTarget(date)
                        .map(t -> t.targetCalories().doubleValue())
                        .orElse(0.0))
                .sum();
    }

    private List<Bucket> buildBuckets(LocalDate from, LocalDate to, Granularity granularity) {
        return switch (granularity) {
            case HOUR -> buildHourBuckets(from, to);
            case DAY -> buildDayBuckets(from, to);
            case WEEK -> buildChunkedBuckets(from, to, 7);
            case MONTH -> buildMonthBuckets(from, to);
        };
    }

    private List<Bucket> buildHourBuckets(LocalDate from, LocalDate to) {
        var buckets = new ArrayList<Bucket>();
        var cursor = dayBoundaryService.startOfMetabolicDay(from);
        var rangeEnd = dayBoundaryService.endOfMetabolicDay(to);
        var now = LocalDateTime.now();
        var effectiveEnd = rangeEnd.isAfter(now) ? now : rangeEnd;

        while (cursor.isBefore(effectiveEnd)) {
            var next = cursor.plusHours(1);
            buckets.add(new Bucket(cursor, next, List.of(dayBoundaryService.metabolicDateOf(cursor))));
            cursor = next;
        }
        return buckets;
    }

    private List<Bucket> buildDayBuckets(LocalDate from, LocalDate to) {
        var buckets = new ArrayList<Bucket>();
        for (var date = from; !date.isAfter(to); date = date.plusDays(1)) {
            buckets.add(new Bucket(dayBoundaryService.startOfMetabolicDay(date),
                    dayBoundaryService.endOfMetabolicDay(date), List.of(date)));
        }
        return buckets;
    }

    private List<Bucket> buildChunkedBuckets(LocalDate from, LocalDate to, int chunkDays) {
        var buckets = new ArrayList<Bucket>();
        var chunkStart = from;
        while (!chunkStart.isAfter(to)) {
            var chunkEndExclusive = minDate(chunkStart.plusDays(chunkDays), to.plusDays(1));
            buckets.add(buildBucket(chunkStart, chunkEndExclusive));
            chunkStart = chunkEndExclusive;
        }
        return buckets;
    }

    private List<Bucket> buildMonthBuckets(LocalDate from, LocalDate to) {
        var buckets = new ArrayList<Bucket>();
        var monthStart = from;
        while (!monthStart.isAfter(to)) {
            var firstOfNextMonth = monthStart.withDayOfMonth(1).plusMonths(1);
            var chunkEndExclusive = minDate(firstOfNextMonth, to.plusDays(1));
            buckets.add(buildBucket(monthStart, chunkEndExclusive));
            monthStart = chunkEndExclusive;
        }
        return buckets;
    }

    private Bucket buildBucket(LocalDate startInclusive, LocalDate endExclusive) {
        var dates = new ArrayList<LocalDate>();
        for (var d = startInclusive; d.isBefore(endExclusive); d = d.plusDays(1)) {
            dates.add(d);
        }
        return new Bucket(dayBoundaryService.startOfMetabolicDay(startInclusive),
                dayBoundaryService.startOfMetabolicDay(endExclusive), dates);
    }

    private static LocalDate minDate(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private record Bucket(LocalDateTime start, LocalDateTime end, List<LocalDate> metabolicDates) {
    }
}
