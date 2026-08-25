package com.chatdiet.food;

import com.chatdiet.dashboard.DailyMacroCacheService;
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
        description = "Correct the calories or macros of the most recently logged food entry. Use only when the correction is linguistically marked (e.g. \"make that two slices\", \"actually it was fried\"). A bare new number is a new food entry, not a correction."
)
public class CorrectFoodEntryTool implements Function<CorrectFoodRequest, ToolResult> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final FoodEntryRepository foodEntryRepository;
    private final DailyMacroCacheService dailyMacroCacheService;

    public CorrectFoodEntryTool(FoodEntryRepository foodEntryRepository, DailyMacroCacheService dailyMacroCacheService) {
        this.foodEntryRepository = foodEntryRepository;
        this.dailyMacroCacheService = dailyMacroCacheService;
    }

    /**
     * Applies the given corrections (any {@code null} field keeps its prior value) to the most
     * recently logged food entry and saves it.
     *
     * @return a {@link ToolResult.NotFound} if there is no food entry to correct, otherwise a
     *         {@link ToolResult.Success} wrapping the updated {@link FoodEntry}
     * @throws RuntimeException if the prior entry values fail to serialize to JSON
     */
    @Override
    public ToolResult apply(CorrectFoodRequest request) {
        var existing = foodEntryRepository.findMostRecent();
        if (existing.isEmpty()) {
            return new ToolResult.NotFound("a recent food entry");
        }

        var prior = existing.get();
        String priorValuesJson;
        try {
            priorValuesJson = OBJECT_MAPPER.writeValueAsString(prior);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize prior food entry values", e);
        }

        var updated = prior.corrected(request.totalCalories(), request.totalProteinG(),
                request.totalCarbsG(), request.totalFatG(), priorValuesJson);
        foodEntryRepository.save(updated);
        dailyMacroCacheService.recomputeForTimestamp(updated.loggedAt());

        return new ToolResult.Success(
                "Corrected \"%s\": %d kcal, %.1fg protein, %.1fg carbs, %.1fg fat."
                        .formatted(updated.rawUtterance(), updated.totalCalories(), updated.totalProteinG(),
                                updated.totalCarbsG(), updated.totalFatG()),
                updated);
    }
}
