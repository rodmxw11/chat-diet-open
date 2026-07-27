package com.chatdiet.food;

import com.chatdiet.fooditem.FoodItemLogger;
import com.chatdiet.fooditem.FoodItemRepository;
import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
@IntentTool(
        name = "log_cached_food",
        intents = {"log_food"},
        description = "Log a packaged food previously logged by UPC scan, matched by name, without rescanning the barcode. Use when the user names a food you've already cached (e.g. \"another Clif bar\"). If nothing matches, fall back to log_food or ask them to scan it."
)
public class LogCachedFoodTool implements Function<LogCachedFoodRequest, ToolResult> {

    private final FoodItemRepository foodItemRepository;
    private final FoodItemLogger foodItemLogger;

    public LogCachedFoodTool(FoodItemRepository foodItemRepository, FoodItemLogger foodItemLogger) {
        this.foodItemRepository = foodItemRepository;
        this.foodItemLogger = foodItemLogger;
    }

    @Override
    public ToolResult apply(LogCachedFoodRequest request) {
        var item = foodItemRepository.findBestMatchByName(request.foodName()).orElse(null);
        if (item == null) {
            return new ToolResult.NotFound("a cached food matching \"" + request.foodName() + "\"");
        }

        var grams = FoodQuantity.resolveGrams(request.quantityG(), request.quantityServings(), item.typicalServingG());
        if (grams.isEmpty()) {
            return new ToolResult.NeedsClarification(
                    "How many servings (or how many grams) did you have of " + item.name() + "?", item.name());
        }

        return foodItemLogger.logScaled(item, grams.getAsDouble());
    }
}
