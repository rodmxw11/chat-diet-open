package com.chatdiet.nutrition;

import com.chatdiet.day.DayBoundaryService;
import com.chatdiet.exercise.ExerciseEntryRepository;
import com.chatdiet.food.FoodEntryRepository;
import com.chatdiet.weight.WeightEntry;
import com.chatdiet.weight.WeightEntryRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Computes and caches each metabolic day's calorie target. Rather than a static BMR-based target,
 * the "effective TDEE" is adaptively nudged toward whatever value would have actually predicted
 * the user's observed weight change between their two most recent weigh-ins, so the target
 * self-corrects over time for individual metabolic variance.
 *
 * <p>Each day's computation starts from the previous day's stored effective TDEE, so the
 * adjustment is a feedback loop and has to converge rather than accumulate: see
 * {@link #adjustForActualWeightChange} for why the prediction is built from the running estimate
 * instead of a fixed BMR.
 */
@Service
public class AdaptiveTargetService {

    /** How aggressively the effective TDEE is nudged toward the fully-corrected value each adjustment; 0=none, 1=full correction. */
    private static final double DAMPING_FACTOR = 0.3;

    /** Energy equivalent of a pound of body mass, the standard approximation. */
    private static final double CALORIES_PER_LB = 3500.0;

    /**
     * Below this many days between weigh-ins, day-to-day water-weight swings (easily a pound or
     * two) swamp the real signal, so the pair is not worth adjusting from.
     */
    private static final int MIN_DAYS_BETWEEN_WEIGH_INS = 2;

    /**
     * Physiological sanity bounds. A value outside these means the adaptive loop has been fed
     * bad data; clamping keeps one bad pair of weigh-ins from poisoning every later target,
     * since each day's computation builds on the previous day's stored value.
     */
    private static final double MIN_PLAUSIBLE_TDEE = 800.0;
    private static final double MAX_PLAUSIBLE_TDEE = 6000.0;

    private final WeightEntryRepository weightEntryRepository;
    private final FoodEntryRepository foodEntryRepository;
    private final ExerciseEntryRepository exerciseEntryRepository;
    private final DailyTargetRepository dailyTargetRepository;
    private final NutritionService nutritionService;
    private final DayBoundaryService dayBoundaryService;

    public AdaptiveTargetService(WeightEntryRepository weightEntryRepository, FoodEntryRepository foodEntryRepository,
                                  ExerciseEntryRepository exerciseEntryRepository, DailyTargetRepository dailyTargetRepository,
                                  NutritionService nutritionService, DayBoundaryService dayBoundaryService) {
        this.weightEntryRepository = weightEntryRepository;
        this.foodEntryRepository = foodEntryRepository;
        this.exerciseEntryRepository = exerciseEntryRepository;
        this.dailyTargetRepository = dailyTargetRepository;
        this.nutritionService = nutritionService;
        this.dayBoundaryService = dayBoundaryService;
    }

    /**
     * Returns the cached target for the given metabolic day, computing and persisting one first
     * if it doesn't exist yet.
     *
     * @return empty if no weight has ever been logged, since there's no baseline to compute from
     */
    public Optional<DailyTarget> getOrComputeTarget(LocalDate metabolicDate) {
        var existing = dailyTargetRepository.findByTargetDate(metabolicDate);
        if (existing.isPresent()) {
            return existing;
        }
        return compute(metabolicDate).map(target -> {
            dailyTargetRepository.save(target);
            return target;
        });
    }

    private Optional<DailyTarget> compute(LocalDate metabolicDate) {
        var recentWeights = weightEntryRepository.findTwoMostRecent();
        if (recentWeights.isEmpty()) {
            return Optional.empty();
        }

        double currentWeight = recentWeights.get(0).weightLbs();
        double baselineEffectiveTdee = dailyTargetRepository.findMostRecent()
                .map(DailyTarget::effectiveTdee)
                .orElseGet(() -> nutritionService.bmr(currentWeight));

        double adjusted = recentWeights.size() == 2
                ? adjustForActualWeightChange(recentWeights, baselineEffectiveTdee)
                : baselineEffectiveTdee;
        double effectiveTdee = Math.clamp(adjusted, MIN_PLAUSIBLE_TDEE, MAX_PLAUSIBLE_TDEE);

        int targetCalories = (int) Math.round(effectiveTdee + nutritionService.dailyGoalDeltaCalories());
        return Optional.of(new DailyTarget(metabolicDate, targetCalories, effectiveTdee, currentWeight));
    }

    /**
     * Nudges the effective TDEE toward whatever value would have predicted the weight change
     * actually observed between the two weigh-ins.
     *
     * <p>The maintenance term the prediction is built from is {@code baselineEffectiveTdee}
     * itself, not the raw Mifflin-St Jeor BMR. That is what makes the loop converge: the
     * resulting step works out to exactly {@code DAMPING_FACTOR * (trueTdee - baseline)},
     * independent of the window length and of how much was eaten, so the error shrinks
     * geometrically as the estimate approaches the truth and vanishes once it gets there.
     * Predicting from a fixed BMR instead leaves a constant error that is re-applied in full on
     * every recomputation, which ramps the stored TDEE without bound while no new weight is
     * logged.
     *
     * @return the baseline unchanged if the weigh-ins are too close together to carry signal
     */
    private double adjustForActualWeightChange(List<WeightEntry> recentWeights, double baselineEffectiveTdee) {
        var latest = recentWeights.get(0);
        var prior = recentWeights.get(1);

        LocalDate startDate = dayBoundaryService.metabolicDateOf(prior.loggedAt());
        LocalDate endDate = dayBoundaryService.metabolicDateOf(latest.loggedAt());
        long daysElapsed = ChronoUnit.DAYS.between(startDate, endDate);
        if (daysElapsed < MIN_DAYS_BETWEEN_WEIGH_INS) {
            return baselineEffectiveTdee;
        }

        // Half-open window: the days whose eating produced the observed change are those from the
        // prior weigh-in up to (not including) the day of the latest one, so exactly daysElapsed
        // days are summed and the per-day average below stays consistent with the divisor.
        double sumIntakeMinusTdee = 0;
        for (LocalDate cursor = startDate; cursor.isBefore(endDate); cursor = cursor.plusDays(1)) {
            var start = dayBoundaryService.startOfMetabolicDay(cursor);
            var end = dayBoundaryService.endOfMetabolicDay(cursor);

            int intake = foodEntryRepository.findByLoggedAtBetween(start, end).stream()
                    .mapToInt(e -> e.totalCalories() != null ? e.totalCalories() : 0)
                    .sum();
            int exerciseBurn = exerciseEntryRepository.findByLoggedAtBetween(start, end).stream()
                    .mapToInt(e -> e.caloriesBurned() != null ? e.caloriesBurned() : 0)
                    .sum();

            sumIntakeMinusTdee += intake - (baselineEffectiveTdee + exerciseBurn);
        }

        double predictedChange = sumIntakeMinusTdee / CALORIES_PER_LB;
        double actualChange = latest.weightLbs() - prior.weightLbs();

        // Losing more than predicted means more was burned than the baseline assumed, so the
        // baseline is too low and must rise - hence predicted minus actual, not the reverse.
        double error = predictedChange - actualChange;

        return baselineEffectiveTdee + DAMPING_FACTOR * error * CALORIES_PER_LB / daysElapsed;
    }
}
