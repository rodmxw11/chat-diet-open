package com.chatdiet.food;

import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
@IntentTool(
        name = "correct_food_entry",
        intents = {"correct_entry"},
        description = "Correct the calories or macros of the most recently logged food entry. Use only when the correction is linguistically marked (e.g. \"make that two slices\", \"actually it was fried\"). A bare new number is a new food entry, not a correction."
)
public class CorrectFoodEntryTool implements Function<CorrectFoodRequest, ToolResult> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final FoodEntryRepository foodEntryRepository;

    public CorrectFoodEntryTool(FoodEntryRepository foodEntryRepository) {
        this.foodEntryRepository = foodEntryRepository;
    }

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

        return new ToolResult.Success(
                "Corrected \"%s\": %d kcal, %.1fg protein, %.1fg carbs, %.1fg fat."
                        .formatted(updated.rawUtterance(), updated.totalCalories(), updated.totalProteinG(),
                                updated.totalCarbsG(), updated.totalFatG()),
                updated);
    }
}
