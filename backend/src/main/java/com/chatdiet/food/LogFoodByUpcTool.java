package com.chatdiet.food;

import com.chatdiet.food.resolve.QuantityResolution;
import com.chatdiet.food.resolve.QuantityResolver;
import com.chatdiet.fooditem.FoodAlias;
import com.chatdiet.fooditem.FoodAliasNormalizer;
import com.chatdiet.fooditem.FoodAliasRepository;
import com.chatdiet.fooditem.FoodItem;
import com.chatdiet.fooditem.FoodItemLogger;
import com.chatdiet.fooditem.FoodItemRepository;
import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import com.chatdiet.openfoodfacts.OpenFoodFactsClient;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * IntentTool that logs a packaged food identified by UPC barcode, looking it up locally first
 * and falling back to {@link OpenFoodFactsClient} (caching the result as a new
 * {@link com.chatdiet.fooditem.FoodItem} for reuse), then scaling its nutrition by the amount
 * eaten.
 */
@Component
@IntentTool(
        name = "log_food_by_upc",
        intents = {"log_food"},
        description = "Log a packaged food identified by UPC barcode. Looks up nutrition from Open Food Facts (caching it for reuse) and scales it by the amount eaten. Provide quantityServings (multiples of the product's typical serving) or quantityG (grams), not both."
)
public class LogFoodByUpcTool implements Function<LogFoodByUpcRequest, ToolResult> {

    private final FoodItemRepository foodItemRepository;
    private final FoodAliasRepository foodAliasRepository;
    private final OpenFoodFactsClient openFoodFactsClient;
    private final FoodItemLogger foodItemLogger;
    private final QuantityResolver quantityResolver;

    public LogFoodByUpcTool(FoodItemRepository foodItemRepository, FoodAliasRepository foodAliasRepository,
                             OpenFoodFactsClient openFoodFactsClient, FoodItemLogger foodItemLogger,
                             QuantityResolver quantityResolver) {
        this.foodItemRepository = foodItemRepository;
        this.foodAliasRepository = foodAliasRepository;
        this.openFoodFactsClient = openFoodFactsClient;
        this.foodItemLogger = foodItemLogger;
        this.quantityResolver = quantityResolver;
    }

    /**
     * Looks up the product by UPC (locally, then via Open Food Facts, caching a new
     * {@code FoodItem} on first lookup) and logs it scaled to the requested quantity.
     *
     * @return a {@link ToolResult.NotFound} if the UPC matches no known or lookup-able product,
     *         a {@link ToolResult.NeedsClarification} if the quantity could not be resolved, or
     *         the logging result otherwise
     */
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
                    off.proteinPer100g(), off.carbsPer100g(), off.fatPer100g(), off.fiberPer100g(),
                    off.sugarPer100g(), off.sodiumMgPer100g(), off.saturatedFatPer100g(),
                    off.cholesterolMgPer100g(), off.potassiumMgPer100g(), off.typicalServingG(), "UPC"));
            // A name-based mention later ("another Coca-Cola") should resolve deterministically too.
            var normalized = FoodAliasNormalizer.normalize(off.name());
            if (foodAliasRepository.findByAliasNormalized(normalized).isEmpty()) {
                foodAliasRepository.save(new FoodAlias(normalized, item.id(), "OFF"));
            }
        }

        var qty = quantityResolver.resolve(item, request.quantityG(), request.quantityServings());
        if (!(qty instanceof QuantityResolution.Grams grams)) {
            return new ToolResult.NeedsClarification(
                    "How many servings (or how many grams) did you have of " + item.name() + "?", item.name());
        }

        return foodItemLogger.logScaled(item, grams.grams(), request.loggedAt());
    }
}
