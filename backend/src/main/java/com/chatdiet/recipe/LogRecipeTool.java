package com.chatdiet.recipe;

import com.chatdiet.food.FoodEntry;
import com.chatdiet.food.FoodEntryRepository;
import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.function.Function;

@Component
@IntentTool(
        name = "log_recipe",
        intents = {"log_food"},
        description = "Log a meal based on an existing recipe, using final totals you've already computed (the recipe scaled to the amount eaten, plus any quizzed extras). Call find_recipe first to get its per-100g values. Falls through to NotFound if no recipe matches - use create_recipe (recipe_intake) or log_food instead."
)
public class LogRecipeTool implements Function<LogRecipeRequest, ToolResult> {

    private final RecipeRepository recipeRepository;
    private final FoodEntryRepository foodEntryRepository;

    public LogRecipeTool(RecipeRepository recipeRepository, FoodEntryRepository foodEntryRepository) {
        this.recipeRepository = recipeRepository;
        this.foodEntryRepository = foodEntryRepository;
    }

    @Override
    public ToolResult apply(LogRecipeRequest request) {
        var recipe = recipeRepository.findBestMatchByName(request.recipeName()).orElse(null);
        if (recipe == null) {
            return new ToolResult.NotFound("a recipe matching \"" + request.recipeName() + "\"");
        }

        if (request.totalCalories() < 10) {
            return new ToolResult.Success("Noted \"" + recipe.name() + "\" - under 10 calories, not logged.", null);
        }

        var entry = new FoodEntry(LocalDateTime.now(), recipe.name(), request.totalCalories(),
                request.totalProteinG(), request.totalCarbsG(), request.totalFatG(), "RECIPE");
        foodEntryRepository.save(entry);
        recipeRepository.save(recipe.withUsageBumped());

        return new ToolResult.Success(
                "Logged \"%s\": %d kcal, %.1fg protein, %.1fg carbs, %.1fg fat."
                        .formatted(recipe.name(), request.totalCalories(), request.totalProteinG(),
                                request.totalCarbsG(), request.totalFatG()),
                entry);
    }
}
