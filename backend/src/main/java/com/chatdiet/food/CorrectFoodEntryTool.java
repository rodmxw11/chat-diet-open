package com.chatdiet.food;

import com.chatdiet.dashboard.DailyMacroCacheService;
import com.chatdiet.fooditem.FoodItemRepository;
import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * IntentTool that revises the calories/macros of the most recently logged {@link FoodEntry},
 * preserving the pre-correction values as JSON on the entry for audit purposes.
 */
@Component
@IntentTool(
        name = "correct_food_entry",
        intents = {"correct_entry"},
        description = "Correct the calories, macros, or weighed amount of the most recently logged food entry. Use only when the correction is linguistically marked (e.g. \"make that two slices\", \"actually it was fried\", \"make that 50g\"). A bare new number is a new food entry, not a correction."
)
public class CorrectFoodEntryTool implements Function<CorrectFoodRequest, ToolResult> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final FoodEntryRepository foodEntryRepository;
    private final FoodItemRepository foodItemRepository;
    private final DailyMacroCacheService dailyMacroCacheService;

    public CorrectFoodEntryTool(FoodEntryRepository foodEntryRepository, FoodItemRepository foodItemRepository,
                                 DailyMacroCacheService dailyMacroCacheService) {
        this.foodEntryRepository = foodEntryRepository;
        this.foodItemRepository = foodItemRepository;
        this.dailyMacroCacheService = dailyMacroCacheService;
    }

    /**
     * Applies the given corrections to the most recently logged food entry and saves it. If
     * {@code amountGrams} is given and the entry references a cached {@link
     * com.chatdiet.fooditem.FoodItem}, calories/macros/micronutrients are recomputed by scaling
     * that item's per-100g values to the new amount rather than applying the request's direct
     * override fields (which are ignored in that case). Otherwise any {@code null} field on the
     * request leaves the corresponding value on the existing entry unchanged.
     *
     * @return a {@link ToolResult.NotFound} if there is no food entry to correct, a
     *         {@link ToolResult.NeedsClarification} if {@code amountGrams} was given but the
     *         entry has no cached item to scale from, otherwise a {@link ToolResult.Success}
     *         wrapping the updated {@link FoodEntry}
     * @throws RuntimeException if the prior entry values fail to serialize to JSON
     */
    @Override
    public ToolResult apply(CorrectFoodRequest request) {
        var existing = foodEntryRepository.findMostRecent();
        if (existing.isEmpty()) {
            return new ToolResult.NotFound("a recent food entry");
        }

        var prior = existing.get();
        FoodEntry updated;

        if (request.amountGrams() != null) {
            if (prior.foodItemId() == null) {
                return new ToolResult.NeedsClarification(
                        "\"" + prior.rawUtterance() + "\" wasn't logged from a cached item, so I can't "
                                + "recompute it from a new weight. What are the corrected calories/macros?",
                        prior);
            }
            var item = foodItemRepository.findById(prior.foodItemId()).orElse(null);
            if (item == null) {
                return new ToolResult.NeedsClarification(
                        "The cached item behind \"" + prior.rawUtterance() + "\" is gone, so I can't "
                                + "recompute it from a new weight. What are the corrected calories/macros?",
                        prior);
            }
            var scaled = item.scaledTo(request.amountGrams());
            updated = prior.corrected(scaled.calories(), scaled.proteinG(), scaled.carbsG(), scaled.fatG(),
                    scaled.fiberG(), scaled.sugarG(), scaled.sodiumMg(), scaled.saturatedFatG(),
                    scaled.cholesterolMg(), scaled.potassiumMg(), request.amountGrams(), toJson(prior));
        } else {
            updated = prior.corrected(request.totalCalories(), request.totalProteinG(), request.totalCarbsG(),
                    request.totalFatG(), request.fiberG(), request.sugarG(), request.sodiumMg(),
                    request.saturatedFatG(), request.cholesterolMg(), request.potassiumMg(), null, toJson(prior));
        }

        foodEntryRepository.save(updated);
        dailyMacroCacheService.recomputeForTimestamp(updated.loggedAt());

        return new ToolResult.Success(
                "Corrected \"%s\": %d kcal, %.1fg protein, %.1fg carbs, %.1fg fat."
                        .formatted(updated.rawUtterance(), updated.totalCalories(), updated.totalProteinG(),
                                updated.totalCarbsG(), updated.totalFatG()),
                updated);
    }

    private static String toJson(FoodEntry entry) {
        try {
            return OBJECT_MAPPER.writeValueAsString(entry);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize prior food entry values", e);
        }
    }
}
