package com.chatdiet.nutrition;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Holds the user's configured weight-change goal rate. The calorie target itself is set manually
 * via {@link GoalService} / {@code set_calorie_goal}, not derived from this - this value is used
 * only to draw the weight-trend goal line and by the independent, self-contained
 * {@link com.chatdiet.projection.ProjectionService}.
 */
@Service
public class NutritionService {

    private final double weeklyRateLbs;

    public NutritionService(@Value("${chat-diet.goal.weekly-rate-lbs:0}") double weeklyRateLbs) {
        this.weeklyRateLbs = weeklyRateLbs;
    }

    /** The configured target weekly weight-change rate in pounds (negative = weight loss goal). */
    public double weeklyRateLbs() {
        return weeklyRateLbs;
    }
}
