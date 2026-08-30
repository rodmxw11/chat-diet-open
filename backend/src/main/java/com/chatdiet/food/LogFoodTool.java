package com.chatdiet.food;

import com.chatdiet.dashboard.DailyMacroCacheService;
import com.chatdiet.fdc.FdcClient;
import com.chatdiet.fdc.FdcProduct;
import com.chatdiet.fooditem.FoodItem;
import com.chatdiet.fooditem.FoodItemLogger;
import com.chatdiet.fooditem.FoodItemRepository;
import com.chatdiet.fooditem.PortionUnitService;
import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * IntentTool that logs a named food item, resolving its nutrition in tiers before falling back
 * to the model's own estimate: (1) the {@code FOOD_ITEM} cache by fuzzy name match, scaled by a
 * weighed gram amount; (2) a USDA FoodData Central lookup, scaled by a weighed gram amount; (3)
 * the cache or an FDC match, scaled by a natural quantity+unit resolved against
 * {@code PORTION_UNIT} (e.g. "1 medium banana" -> 118g); (4) the model-estimated totals supplied
 * directly in the request. Tiers 1-3 all require either a weighed gram amount or a
 * resolvable unit to scale their per-100g data to; without either, resolution goes straight to
 * the model's estimate.
 *
 * <p>A tier-2, tier-3, or tier-4 result is cached as a new {@link FoodItem} (when an amount is
 * known, so a per-100g figure can be derived) so every later mention of the same food is
 * deterministic instead of re-estimating from scratch. A newly FDC-cached item also gets its
 * known portions fetched and stored, and any request that states both a quantity+unit *and* a
 * gram amount teaches {@code PORTION_UNIT} that unit's real weight for next time - the two
 * together are what let ordinary, non-gram-weighed utterances ("a bowl of oatmeal" doesn't
 * qualify, but "2 eggs" or "1 medium banana" does) reach the deterministic tiers at all, instead
 * of hitting the LLM estimator on every single mention.
 */
@Component
@IntentTool(
        name = "log_food",
        intents = {"log_food"},
        description = "Log a named food item. If the user states a weighed amount in grams, provide " +
                "amountGrams so the app can check its own food cache and USDA FoodData Central for real " +
                "nutrition data first. If instead they state a natural count (e.g. \"2 eggs\", \"1 medium " +
                "banana\", \"1 cup of rice\"), provide quantity and unit so the app can try to resolve that " +
                "unit to a known gram weight before falling back to your estimate. Always still provide your " +
                "own best-effort estimate of calories, macros, and micronutrients (fiber, sugar, sodium, " +
                "saturated fat, cholesterol, potassium) as a fallback for when nothing resolves. Named foods " +
                "only - not for UPC-based entries. Do not call for items under 10 calories (e.g. black tea, " +
                "water); just acknowledge those in the reply."
)
public class LogFoodTool implements Function<LogFoodRequest, ToolResult> {

    private final FoodEntryRepository foodEntryRepository;
    private final FoodItemRepository foodItemRepository;
    private final FdcClient fdcClient;
    private final FoodItemLogger foodItemLogger;
    private final PortionUnitService portionUnitService;
    private final DailyMacroCacheService dailyMacroCacheService;
    private final FoodLogVerificationContext foodLogVerificationContext;

    public LogFoodTool(FoodEntryRepository foodEntryRepository, FoodItemRepository foodItemRepository,
                        FdcClient fdcClient, FoodItemLogger foodItemLogger, PortionUnitService portionUnitService,
                        DailyMacroCacheService dailyMacroCacheService,
                        FoodLogVerificationContext foodLogVerificationContext) {
        this.foodEntryRepository = foodEntryRepository;
        this.foodItemRepository = foodItemRepository;
        this.fdcClient = fdcClient;
        this.foodItemLogger = foodItemLogger;
        this.portionUnitService = portionUnitService;
        this.dailyMacroCacheService = dailyMacroCacheService;
        this.foodLogVerificationContext = foodLogVerificationContext;
    }

    @Override
    public ToolResult apply(LogFoodRequest request) {
        if (request.amountGrams() != null && request.amountGrams() > 0) {
            return logByGrams(request);
        }
        if (hasQuantityAndUnit(request)) {
            var resolved = logByQuantityUnit(request);
            if (resolved != null) {
                return resolved;
            }
        }
        return logFromEstimate(request);
    }

    /** Tier 1/2: cache or FDC, scaled by a stated gram amount. */
    private ToolResult logByGrams(LogFoodRequest request) {
        var cached = foodItemRepository.findBestMatchByName(request.description());
        if (cached.isPresent()) {
            maybeLearnPortion(cached.get().id(), request);
            return foodItemLogger.logScaled(cached.get(), request.amountGrams(), request.loggedAt());
        }

        var fdcMatch = fdcClient.search(request.description());
        if (fdcMatch.isPresent()) {
            var item = cacheFdcMatch(request.description(), fdcMatch.get());
            maybeLearnPortion(item.id(), request);
            return foodItemLogger.logScaled(item, request.amountGrams(), request.loggedAt());
        }

        return logFromEstimate(request);
    }

    /**
     * Tier 3: cache or FDC, scaled by a natural quantity+unit resolved against
     * {@code PORTION_UNIT} - returns {@code null} (rather than a result) when nothing resolves,
     * so the caller falls through to the model's estimate instead of treating "unit unknown" as
     * a hard failure.
     */
    private ToolResult logByQuantityUnit(LogFoodRequest request) {
        FoodItem item;
        var cached = foodItemRepository.findBestMatchByName(request.description());
        if (cached.isPresent()) {
            item = cached.get();
        } else {
            var fdcMatch = fdcClient.search(request.description());
            if (fdcMatch.isEmpty()) {
                return null;
            }
            item = cacheFdcMatch(request.description(), fdcMatch.get());
        }

        var gramsPerUnit = portionUnitService.resolveGrams(item.id(), request.unit());
        if (gramsPerUnit.isEmpty()) {
            return null;
        }
        return foodItemLogger.logScaled(item, gramsPerUnit.getAsDouble() * request.quantity(), request.loggedAt());
    }

    /** Caches a new FDC match as a {@link FoodItem} and fetches/stores its known portions. */
    private FoodItem cacheFdcMatch(String description, FdcProduct product) {
        var item = foodItemRepository.save(new FoodItem(description, null,
                product.caloriesPer100g(), product.proteinPer100g(), product.carbsPer100g(),
                product.fatPer100g(), product.fiberPer100g(), product.sugarPer100g(),
                product.sodiumMgPer100g(), product.saturatedFatPer100g(), product.cholesterolMgPer100g(),
                product.potassiumMgPer100g(), product.typicalServingG(), "FDC"));
        if (product.fdcId() != null) {
            portionUnitService.populateFromFdc(item.id(), product.fdcId());
        }
        return item;
    }

    private void maybeLearnPortion(long foodItemId, LogFoodRequest request) {
        if (hasQuantityAndUnit(request)) {
            portionUnitService.learnFromWeighedEntry(foodItemId, request.unit(), request.quantity(),
                    request.amountGrams());
        }
    }

    private static boolean hasQuantityAndUnit(LogFoodRequest request) {
        return request.quantity() != null && request.quantity() > 0
                && request.unit() != null && !request.unit().isBlank();
    }

    /** Tier 4: nothing resolved - use the model's estimate. */
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
            maybeLearnPortion(newFoodItemId, request);
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
