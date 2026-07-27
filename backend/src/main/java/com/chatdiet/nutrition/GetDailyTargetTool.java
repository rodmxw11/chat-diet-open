package com.chatdiet.nutrition;

import com.chatdiet.day.DayBoundaryService;
import com.chatdiet.food.FoodEntryRepository;
import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
@IntentTool(
        name = "get_daily_target",
        intents = {"query_data"},
        description = "Get today's calorie target, effective TDEE, and how many calories have been consumed and remain today."
)
public class GetDailyTargetTool implements Function<GetDailyTargetRequest, ToolResult> {

    private final AdaptiveTargetService adaptiveTargetService;
    private final FoodEntryRepository foodEntryRepository;
    private final DayBoundaryService dayBoundaryService;

    public GetDailyTargetTool(AdaptiveTargetService adaptiveTargetService, FoodEntryRepository foodEntryRepository,
                               DayBoundaryService dayBoundaryService) {
        this.adaptiveTargetService = adaptiveTargetService;
        this.foodEntryRepository = foodEntryRepository;
        this.dayBoundaryService = dayBoundaryService;
    }

    @Override
    public ToolResult apply(GetDailyTargetRequest request) {
        var today = dayBoundaryService.today();
        var target = adaptiveTargetService.getOrComputeTarget(today);
        if (target.isEmpty()) {
            return new ToolResult.NotFound("a calorie target - log a weight first");
        }

        var start = dayBoundaryService.startOfMetabolicDay(today);
        var end = dayBoundaryService.endOfMetabolicDay(today);
        int consumed = foodEntryRepository.findByLoggedAtBetween(start, end).stream()
                .mapToInt(e -> e.totalCalories() != null ? e.totalCalories() : 0)
                .sum();
        int remaining = target.get().targetCalories() - consumed;

        return new ToolResult.Success(
                "Today's target: %d kcal (effective TDEE %.0f). Consumed so far: %d kcal. Remaining: %d kcal."
                        .formatted(target.get().targetCalories(), target.get().effectiveTdee(), consumed, remaining),
                target.get());
    }
}
