package com.chatdiet.nutrition;

import com.chatdiet.day.DayBoundaryService;
import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * IntentTool backing {@code set_calorie_goal}: sets the daily calorie target effective from a
 * given date (defaulting to today), replacing the old TDEE-based adaptive target with a value
 * the user adjusts themselves.
 */
@Component
@IntentTool(
        name = "set_calorie_goal",
        intents = {"manage_goal"},
        description = "Set the daily calorie target, effective from a given date (defaults to today) until changed again."
)
public class SetCalorieGoalTool implements Function<SetCalorieGoalRequest, ToolResult> {

    private final GoalService goalService;
    private final DayBoundaryService dayBoundaryService;

    public SetCalorieGoalTool(GoalService goalService, DayBoundaryService dayBoundaryService) {
        this.goalService = goalService;
        this.dayBoundaryService = dayBoundaryService;
    }

    @Override
    public ToolResult apply(SetCalorieGoalRequest request) {
        var effectiveFrom = request.effectiveFrom() != null ? request.effectiveFrom() : dayBoundaryService.today();
        var target = goalService.setGoal(effectiveFrom, request.targetCalories());
        return new ToolResult.Success(
                "Calorie goal set to %d kcal/day, effective %s.".formatted(target.targetCalories(), target.targetDate()),
                target);
    }
}
