package com.chatdiet.fooditem;

import com.chatdiet.food.FoodEntry;
import com.chatdiet.food.FoodEntryRepository;
import com.chatdiet.food.LoggedAtResolver;
import com.chatdiet.intent.ToolResult;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** Shared by the UPC-scan and cached-food-by-name logging tools. */
@Component
public class FoodItemLogger {

    private final FoodEntryRepository foodEntryRepository;
    private final FoodItemRepository foodItemRepository;

    public FoodItemLogger(FoodEntryRepository foodEntryRepository, FoodItemRepository foodItemRepository) {
        this.foodEntryRepository = foodEntryRepository;
        this.foodItemRepository = foodItemRepository;
    }

    /**
     * Logs a {@link com.chatdiet.food.FoodEntry} for the given cached food item scaled to the
     * given portion size, and bumps the item's usage count/timestamp.
     *
     * @param grams    portion size in grams
     * @param loggedAt when this was actually eaten, if backdated; null to log under the current
     *                 time
     * @return a {@link ToolResult.Success} describing the logged entry
     */
    public ToolResult.Success logScaled(FoodItem item, double grams, LocalDateTime loggedAt) {
        var scaled = item.scaledTo(grams);
        var entry = new FoodEntry(LoggedAtResolver.resolve(loggedAt), item.name(), scaled.calories(),
                scaled.proteinG(), scaled.carbsG(), scaled.fatG(), item.lookupSource());
        foodEntryRepository.save(entry);
        foodItemRepository.save(item.withUsageBumped());

        return new ToolResult.Success(
                "Logged \"%s\" (%.0fg): %d kcal, %.1fg protein, %.1fg carbs, %.1fg fat."
                        .formatted(item.name(), grams, scaled.calories(), scaled.proteinG(),
                                scaled.carbsG(), scaled.fatG()),
                entry);
    }
}
