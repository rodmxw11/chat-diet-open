package com.chatdiet.recipe;

import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * IntentTool callback for the {@code create_recipe} intent (under {@code recipe_intake}):
 * creates a new reusable recipe from total nutrition and weight, normalizing the given totals to
 * per-100g values and saving any supplied ingredient lines.
 */
@Component
@IntentTool(
        name = "create_recipe",
        intents = {"recipe_intake"},
        description = "Create a new reusable recipe from total nutrition and weight. Use pot-level totals (whole batch weighed) when available for a confident recipe; otherwise use a single weighed serving's totals and set provisional=true. Call find_recipe first to avoid duplicating an existing recipe."
)
public class CreateRecipeTool implements Function<CreateRecipeRequest, ToolResult> {

    private final RecipeRepository recipeRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;

    public CreateRecipeTool(RecipeRepository recipeRepository, RecipeIngredientRepository recipeIngredientRepository) {
        this.recipeRepository = recipeRepository;
        this.recipeIngredientRepository = recipeIngredientRepository;
    }

    /**
     * Saves a new {@link Recipe} with nutrition scaled to per-100g from {@code request}'s
     * batch/serving totals, plus any ingredient lines.
     */
    @Override
    public ToolResult apply(CreateRecipeRequest request) {
        double factor = 100.0 / request.totalWeightG();
        var recipe = recipeRepository.save(new Recipe(
                request.name(),
                request.totalCalories() * factor,
                request.totalProteinG() * factor,
                request.totalCarbsG() * factor,
                request.totalFatG() * factor,
                request.typicalServingG(),
                request.totalWeightG(),
                request.provisional()));

        if (request.ingredients() != null) {
            for (var ingredient : request.ingredients()) {
                recipeIngredientRepository.save(
                        new RecipeIngredient(recipe.id(), ingredient.description(), ingredient.variable()));
            }
        }

        return new ToolResult.Success(
                "Saved recipe \"%s\"%s: %.0f kcal, %.1fg protein, %.1fg carbs, %.1fg fat per 100g."
                        .formatted(recipe.name(), request.provisional() ? " (provisional)" : "",
                                recipe.per100gCalories(), recipe.per100gProtein(), recipe.per100gCarbs(),
                                recipe.per100gFat()),
                recipe);
    }
}
