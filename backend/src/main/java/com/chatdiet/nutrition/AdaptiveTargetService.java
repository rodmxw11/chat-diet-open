package com.chatdiet.nutrition;

import com.chatdiet.day.DayBoundaryService;
import com.chatdiet.exercise.ExerciseEntryRepository;
import com.chatdiet.food.FoodEntryRepository;
import com.chatdiet.weight.WeightEntry;
import com.chatdiet.weight.WeightEntryRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Computes and caches each metabolic day's calorie target. Rather than a static BMR-based target,
 * the "effective TDEE" is adaptively nudged toward whatever value would have actually predicted
 * the user's observed weight change between their two most recent weigh-ins, so the target
 * self-corrects over time for individual metabolic variance.
 */
@Service
public class AdaptiveTargetService {

    /** How aggressively the effective TDEE is nudged toward the fully-corrected value each adjustment; 0=none, 1=full correction. */
    private static final double DAMPING_FACTOR = 0.3;

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

        double effectiveTdee = recentWeights.size() == 2
                ? adjustForActualWeightChange(recentWeights, baselineEffectiveTdee)
                : baselineEffectiveTdee;

        int targetCalories = (int) Math.round(effectiveTdee + nutritionService.dailyGoalDeltaCalories());
        return Optional.of(new DailyTarget(metabolicDate, targetCalories, effectiveTdee, currentWeight));
    }

    /**
     * BMR across the window between the two weigh-ins is approximated with their average
     * weight (SPEC doesn't specify a per-day weight trajectory), since only the two
     * endpoints are known.
     */
    private double adjustForActualWeightChange(List<WeightEntry> recentWeights, double baselineEffectiveTdee) {
        var latest = recentWeights.get(0);
        var prior = recentWeights.get(1);

        long daysElapsed = Math.max(1, Duration.between(prior.loggedAt(), latest.loggedAt()).toDays());
        double avgWeight = (latest.weightLbs() + prior.weightLbs()) / 2.0;
        double assumedDailyBmr = nutritionService.bmr(avgWeight);

        double sumIntakeMinusTdee = 0;
        LocalDate cursor = dayBoundaryService.metabolicDateOf(prior.loggedAt());
        LocalDate endDate = dayBoundaryService.metabolicDateOf(latest.loggedAt());
        while (!cursor.isAfter(endDate)) {
            var start = dayBoundaryService.startOfMetabolicDay(cursor);
            var end = dayBoundaryService.endOfMetabolicDay(cursor);

            int intake = foodEntryRepository.findByLoggedAtBetween(start, end).stream()
                    .mapToInt(e -> e.totalCalories() != null ? e.totalCalories() : 0)
                    .sum();
            int exerciseBurn = exerciseEntryRepository.findByLoggedAtBetween(start, end).stream()
                    .mapToInt(e -> e.caloriesBurned() != null ? e.caloriesBurned() : 0)
                    .sum();

            sumIntakeMinusTdee += intake - (assumedDailyBmr + exerciseBurn);
            cursor = cursor.plusDays(1);
        }

        double predictedChange = sumIntakeMinusTdee / 3500.0;
        double actualChange = latest.weightLbs() - prior.weightLbs();
        double error = actualChange - predictedChange;

        return baselineEffectiveTdee + DAMPING_FACTOR * error * 3500.0 / daysElapsed;
    }
}
