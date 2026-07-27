package com.chatdiet.food;

import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.function.Function;

@Component
@IntentTool(
        name = "log_food",
        intents = {"log_food"},
        description = "Log a named food item with estimated calories and macros. Named foods only - not for UPC, photo, menu, or recipe-based entries. Do not call for items under 10 calories (e.g. black tea, water); just acknowledge those in the reply."
)
public class LogFoodTool implements Function<LogFoodRequest, ToolResult> {

    private final FoodEntryRepository foodEntryRepository;

    public LogFoodTool(FoodEntryRepository foodEntryRepository) {
        this.foodEntryRepository = foodEntryRepository;
    }

    @Override
    public ToolResult apply(LogFoodRequest request) {
        if (request.totalCalories() < 10) {
            return new ToolResult.Success(
                    "Noted \"" + request.description() + "\" - under 10 calories, not logged.", null);
        }

        var entry = new FoodEntry(LocalDateTime.now(), request.description(), request.totalCalories(),
                request.totalProteinG(), request.totalCarbsG(), request.totalFatG(), "MANUAL");
        foodEntryRepository.save(entry);

        return new ToolResult.Success(
                "Logged \"%s\": %d kcal, %.1fg protein, %.1fg carbs, %.1fg fat."
                        .formatted(entry.rawUtterance(), entry.totalCalories(), entry.totalProteinG(),
                                entry.totalCarbsG(), entry.totalFatG()),
                entry);
    }
}
