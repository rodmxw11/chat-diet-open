package com.chatdiet.food;

import com.chatdiet.fooditem.FoodItem;
import com.chatdiet.fooditem.FoodItemLogger;
import com.chatdiet.fooditem.FoodItemRepository;
import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import com.chatdiet.openfoodfacts.OpenFoodFactsClient;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
@IntentTool(
        name = "log_food_by_upc",
        intents = {"log_food"},
        description = "Log a packaged food identified by UPC barcode. Looks up nutrition from Open Food Facts (caching it for reuse) and scales it by the amount eaten. Provide quantityServings (multiples of the product's typical serving) or quantityG (grams), not both."
)
public class LogFoodByUpcTool implements Function<LogFoodByUpcRequest, ToolResult> {

    private final FoodItemRepository foodItemRepository;
    private final OpenFoodFactsClient openFoodFactsClient;
    private final FoodItemLogger foodItemLogger;

    public LogFoodByUpcTool(FoodItemRepository foodItemRepository, OpenFoodFactsClient openFoodFactsClient,
                             FoodItemLogger foodItemLogger) {
        this.foodItemRepository = foodItemRepository;
        this.openFoodFactsClient = openFoodFactsClient;
        this.foodItemLogger = foodItemLogger;
    }

    @Override
    public ToolResult apply(LogFoodByUpcRequest request) {
        var item = foodItemRepository.findByUpc(request.upc()).orElse(null);
        if (item == null) {
            var looked = openFoodFactsClient.lookup(request.upc());
            if (looked.isEmpty()) {
                return new ToolResult.NotFound("a product for UPC " + request.upc());
            }
            var off = looked.get();
            item = foodItemRepository.save(new FoodItem(off.name(), request.upc(), off.caloriesPer100g(),
                    off.proteinPer100g(), off.carbsPer100g(), off.fatPer100g(), off.typicalServingG(), "UPC"));
        }

        var grams = FoodQuantity.resolveGrams(request.quantityG(), request.quantityServings(), item.typicalServingG());
        if (grams.isEmpty()) {
            return new ToolResult.NeedsClarification(
                    "How many servings (or how many grams) did you have of " + item.name() + "?", item.name());
        }

        return foodItemLogger.logScaled(item, grams.getAsDouble());
    }
}
