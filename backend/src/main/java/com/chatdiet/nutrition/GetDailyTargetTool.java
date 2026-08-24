package com.chatdiet.nutrition;

import com.chatdiet.day.DayBoundaryService;
import com.chatdiet.food.FoodEntryRepository;
import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * IntentTool implementation backing {@code get_daily_target}: reports today's calorie target (if
 * any has ever been set) and how many calories have been consumed and remain for the current
 * metabolic day.
 */
@Component
@IntentTool(
        name = "get_daily_target",
        intents = {"query_data"},
        description = "Get today's calorie target and how many calories have been consumed and remain today."
)
public class GetDailyTargetTool implements Function<GetDailyTargetRequest, ToolResult> {

    private final GoalService goalService;
    private final FoodEntryRepository foodEntryRepository;
    private final DayBoundaryService dayBoundaryService;

    public GetDailyTargetTool(GoalService goalService, FoodEntryRepository foodEntryRepository,
                               DayBoundaryService dayBoundaryService) {
        this.goalService = goalService;
        this.foodEntryRepository = foodEntryRepository;
        this.dayBoundaryService = dayBoundaryService;
    }

    /**
     * @return {@link ToolResult.NotFound} if no goal has ever been set, otherwise a
     *         {@link ToolResult.Success} summarizing today's target/consumed/remaining
     */
    @Override
    public ToolResult apply(GetDailyTargetRequest request) {
        var today = dayBoundaryService.today();
        var target = goalService.targetFor(today);
        if (target.isEmpty()) {
            return new ToolResult.NotFound("a calorie goal - set one first with set_calorie_goal");
        }

        var start = dayBoundaryService.startOfMetabolicDay(today);
        var end = dayBoundaryService.endOfMetabolicDay(today);
        int consumed = foodEntryRepository.findByLoggedAtBetween(start, end).stream()
                .mapToInt(e -> e.totalCalories() != null ? e.totalCalories() : 0)
                .sum();
        int remaining = target.get().targetCalories() - consumed;

        return new ToolResult.Success(
                "Today's target: %d kcal. Consumed so far: %d kcal. Remaining: %d kcal."
                        .formatted(target.get().targetCalories(), consumed, remaining),
                target.get());
    }
}
