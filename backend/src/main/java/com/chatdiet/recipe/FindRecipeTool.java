package com.chatdiet.recipe;

import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import org.springframework.stereotype.Component;

import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * IntentTool callback for the {@code find_recipe} intent (under {@code recipe_intake} and
 * {@code log_food}): looks up an existing recipe by (fuzzy) name and returns its per-100g
 * nutrition, typical serving size, provisional flag, and which ingredients are marked variable.
 */
@Component
@IntentTool(
        name = "find_recipe",
        intents = {"recipe_intake", "log_food"},
        description = "Look up an existing recipe by (fuzzy) name. Returns its per-100g nutrition, typical serving size, provisional flag, and which ingredients are marked variable (worth asking about when reusing). Use before creating a new recipe, and before logging a recipe-based meal."
)
public class FindRecipeTool implements Function<FindRecipeRequest, ToolResult> {

    private final RecipeRepository recipeRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;

    public FindRecipeTool(RecipeRepository recipeRepository, RecipeIngredientRepository recipeIngredientRepository) {
        this.recipeRepository = recipeRepository;
        this.recipeIngredientRepository = recipeIngredientRepository;
    }

    /**
     * @return {@link ToolResult.Success} with the matched recipe and a summary noting any
     *         variable ingredients, or {@link ToolResult.NotFound} if no recipe matches
     */
    @Override
    public ToolResult apply(FindRecipeRequest request) {
        var recipe = recipeRepository.findBestMatchByName(request.name()).orElse(null);
        if (recipe == null) {
            return new ToolResult.NotFound("a recipe matching \"" + request.name() + "\"");
        }

        var variableIngredients = recipeIngredientRepository.findByRecipeId(recipe.id()).stream()
                .filter(i -> Boolean.TRUE.equals(i.variableIngredient()))
                .map(RecipeIngredient::description)
                .collect(Collectors.joining(", "));

        return new ToolResult.Success(
                "\"%s\"%s: %.0f kcal, %.1fg protein, %.1fg carbs, %.1fg fat per 100g, typical serving %s.%s"
                        .formatted(recipe.name(), Boolean.TRUE.equals(recipe.provisional()) ? " (provisional)" : "",
                                recipe.per100gCalories(), recipe.per100gProtein(), recipe.per100gCarbs(),
                                recipe.per100gFat(),
                                recipe.typicalServingG() != null ? recipe.typicalServingG() + "g" : "unknown",
                                variableIngredients.isEmpty()
                                        ? ""
                                        : " Variable ingredients to ask about: " + variableIngredients + "."),
                recipe);
    }
}
