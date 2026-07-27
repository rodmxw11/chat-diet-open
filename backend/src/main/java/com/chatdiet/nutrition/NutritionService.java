package com.chatdiet.nutrition;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;

@Service
public class NutritionService {

    private final Sex sex;
    private final LocalDate birthDate;
    private final double heightIn;
    private final double weeklyRateLbs;

    public NutritionService(
            @Value("${chat-diet.profile.sex}") String sex,
            @Value("${chat-diet.profile.birth-date}") String birthDate,
            @Value("${chat-diet.profile.height-in}") double heightIn,
            @Value("${chat-diet.goal.weekly-rate-lbs:0}") double weeklyRateLbs) {
        this.sex = Sex.valueOf(sex.toUpperCase());
        this.birthDate = LocalDate.parse(birthDate);
        this.heightIn = heightIn;
        this.weeklyRateLbs = weeklyRateLbs;
    }

    public int ageYears() {
        return Period.between(birthDate, LocalDate.now()).getYears();
    }

    /** Mifflin-St Jeor BMR, in calories/day, for a given body weight. */
    public double bmr(double weightLbs) {
        double weightKg = weightLbs * 0.453592;
        double heightCm = heightIn * 2.54;
        double base = 10 * weightKg + 6.25 * heightCm - 5 * ageYears();
        return sex == Sex.MALE ? base + 5 : base - 161;
    }

    /** Daily calorie delta implied by the configured weekly rate of change (negative = deficit). */
    public double dailyGoalDeltaCalories() {
        return weeklyRateLbs * 3500.0 / 7.0;
    }

    public double weeklyRateLbs() {
        return weeklyRateLbs;
    }
}
