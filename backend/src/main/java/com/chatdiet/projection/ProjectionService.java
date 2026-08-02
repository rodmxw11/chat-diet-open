package com.chatdiet.projection;

import com.chatdiet.nutrition.NutritionService;
import com.chatdiet.weight.WeightEntryRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Projects future weight based on the most recent logged weight and the app's configured
 * weekly weight-change rate.
 */
@Service
public class ProjectionService {

    private final WeightEntryRepository weightEntryRepository;
    private final NutritionService nutritionService;

    public ProjectionService(WeightEntryRepository weightEntryRepository, NutritionService nutritionService) {
        this.weightEntryRepository = weightEntryRepository;
        this.nutritionService = nutritionService;
    }

    /** Projected date the goal weight is reached, assuming the configured weekly rate holds. */
    public Optional<LocalDate> projectGoalDate(double goalWeightLbs) {
        var current = weightEntryRepository.findMostRecent();
        if (current.isEmpty()) {
            return Optional.empty();
        }

        double dailyRateLbs = nutritionService.weeklyRateLbs() / 7.0;
        if (dailyRateLbs == 0) {
            return Optional.empty();
        }

        double daysNeeded = (goalWeightLbs - current.get().weightLbs()) / dailyRateLbs;
        if (daysNeeded < 0) {
            return Optional.empty();
        }

        return Optional.of(LocalDate.now().plusDays(Math.round(daysNeeded)));
    }

    /** Projected weight on a future date, assuming the configured weekly rate holds. */
    public Optional<Double> projectWeightOn(LocalDate date) {
        var current = weightEntryRepository.findMostRecent();
        if (current.isEmpty()) {
            return Optional.empty();
        }

        double dailyRateLbs = nutritionService.weeklyRateLbs() / 7.0;
        long days = ChronoUnit.DAYS.between(LocalDate.now(), date);
        return Optional.of(current.get().weightLbs() + dailyRateLbs * days);
    }
}
