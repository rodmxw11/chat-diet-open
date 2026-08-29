package com.chatdiet.food;

import com.chatdiet.dashboard.DailyMacroCacheService;
import com.chatdiet.fdc.FdcClient;
import com.chatdiet.fooditem.FoodItem;
import com.chatdiet.fooditem.FoodItemLogger;
import com.chatdiet.fooditem.FoodItemRepository;
import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * IntentTool that logs a named food item, resolving its nutrition in three tiers before falling
 * back to the model's own estimate: (1) the {@code FOOD_ITEM} cache by fuzzy name match, (2) a
 * USDA FoodData Central lookup for raw/generic foods, (3) the model-estimated totals supplied
 * directly in the request. Tiers 1 and 2 both require a weighed gram amount to scale their
 * per-100g data to; without one, resolution goes straight to the model's estimate.
 *
 * <p>A tier-2 or tier-3 result is cached as a new {@link FoodItem} (when a gram amount is known,
 * so a per-100g figure can be derived) so every later mention of the same food is deterministic
 * instead of re-estimating from scratch.
 */
@Component
@IntentTool(
        name = "log_food",
        intents = {"log_food"},
        description = "Log a named food item. If the user states a weighed amount in grams, provide " +
                "amountGrams so the app can check its own food cache and USDA FoodData Central for real " +
                "nutrition data first. Always still provide your own best-effort estimate of calories, " +
                "macros, and micronutrients (fiber, sugar, sodium, saturated fat, cholesterol, potassium) " +
                "as a fallback for when neither source has a match. Named foods only - not for UPC-based " +
                "entries. Do not call for items under 10 calories (e.g. black tea, water); just " +
                "acknowledge those in the reply."
)
public class LogFoodTool implements Function<LogFoodRequest, ToolResult> {

    private final FoodEntryRepository foodEntryRepository;
    private final FoodItemRepository foodItemRepository;
    private final FdcClient fdcClient;
    private final FoodItemLogger foodItemLogger;
    private final DailyMacroCacheService dailyMacroCacheService;
    private final FoodLogVerificationContext foodLogVerificationContext;

    public LogFoodTool(FoodEntryRepository foodEntryRepository, FoodItemRepository foodItemRepository,
                        FdcClient fdcClient, FoodItemLogger foodItemLogger,
                        DailyMacroCacheService dailyMacroCacheService,
                        FoodLogVerificationContext foodLogVerificationContext) {
        this.foodEntryRepository = foodEntryRepository;
        this.foodItemRepository = foodItemRepository;
        this.fdcClient = fdcClient;
        this.foodItemLogger = foodItemLogger;
        this.dailyMacroCacheService = dailyMacroCacheService;
        this.foodLogVerificationContext = foodLogVerificationContext;
    }

    @Override
    public ToolResult apply(LogFoodRequest request) {
        if (request.amountGrams() != null && request.amountGrams() > 0) {
            var cached = foodItemRepository.findBestMatchByName(request.description());
            if (cached.isPresent()) {
                return foodItemLogger.logScaled(cached.get(), request.amountGrams(), request.loggedAt());
            }

            var fdcMatch = fdcClient.search(request.description());
            if (fdcMatch.isPresent()) {
                var product = fdcMatch.get();
                var item = foodItemRepository.save(new FoodItem(request.description(), null,
                        product.caloriesPer100g(), product.proteinPer100g(), product.carbsPer100g(),
                        product.fatPer100g(), product.fiberPer100g(), product.sugarPer100g(),
                        product.sodiumMgPer100g(), product.saturatedFatPer100g(), product.cholesterolMgPer100g(),
                        product.potassiumMgPer100g(), product.typicalServingG(), "FDC"));
                return foodItemLogger.logScaled(item, request.amountGrams(), request.loggedAt());
            }
        }

        return logFromEstimate(request);
    }

    /** Tier 3: no cache/FDC match (or no gram amount to scale one to) - use the model's estimate. */
    private ToolResult logFromEstimate(LogFoodRequest request) {
        if (request.totalCalories() == null) {
            return new ToolResult.NeedsClarification(
                    "How many calories (and macros, if you can estimate them) should I log for \""
                            + request.description() + "\"?",
                    request.description());
        }
        if (request.totalCalories() < 10) {
            return new ToolResult.Success(
                    "Noted \"" + request.description() + "\" - under 10 calories, not logged.", null);
        }

        // A known gram amount lets this estimate be cached as a reusable per-100g FoodItem, so a
        // repeat mention of the same food is deterministic instead of re-estimated from scratch.
        Long newFoodItemId = null;
        if (request.amountGrams() != null && request.amountGrams() > 0) {
            double factor = 100.0 / request.amountGrams();
            var item = foodItemRepository.save(new FoodItem(request.description(), null,
                    request.totalCalories() * factor, scale(request.totalProteinG(), factor),
                    scale(request.totalCarbsG(), factor), scale(request.totalFatG(), factor),
                    scale(request.fiberG(), factor), scale(request.sugarG(), factor),
                    scale(request.sodiumMg(), factor), scale(request.saturatedFatG(), factor),
                    scale(request.cholesterolMg(), factor), scale(request.potassiumMg(), factor),
                    null, "MODEL_ESTIMATE"));
            newFoodItemId = item.id();
        }

        var entry = new FoodEntry(LoggedAtResolver.resolve(request.loggedAt()), request.description(),
                request.totalCalories(), request.totalProteinG(), request.totalCarbsG(), request.totalFatG(),
                request.fiberG(), request.sugarG(), request.sodiumMg(), request.saturatedFatG(),
                request.cholesterolMg(), request.potassiumMg(), newFoodItemId, request.amountGrams(), "MANUAL");
        foodEntryRepository.save(entry);
        dailyMacroCacheService.recomputeForTimestamp(entry.loggedAt());
        foodLogVerificationContext.markLogged();

        return new ToolResult.Success(
                "Logged \"%s\": %d kcal, %.1fg protein, %.1fg carbs, %.1fg fat."
                        .formatted(entry.rawUtterance(), entry.totalCalories(), orZero(entry.totalProteinG()),
                                orZero(entry.totalCarbsG()), orZero(entry.totalFatG())),
                entry);
    }

    private static Double scale(Double value, double factor) {
        return value != null ? value * factor : null;
    }

    private static double orZero(Double value) {
        return value != null ? value : 0.0;
    }
}
