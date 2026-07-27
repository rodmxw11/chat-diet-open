package com.chatdiet.recipe;

import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
@IntentTool(
        name = "refine_recipe",
        intents = {"recipe_intake"},
        description = "Refine an existing (usually provisional) recipe with better total weight and nutrition once more detail is available, clearing the provisional flag. Only applies to future logs - past entries are never recalculated."
)
public class RefineRecipeTool implements Function<RefineRecipeRequest, ToolResult> {

    private final RecipeRepository recipeRepository;

    public RefineRecipeTool(RecipeRepository recipeRepository) {
        this.recipeRepository = recipeRepository;
    }

    @Override
    public ToolResult apply(RefineRecipeRequest request) {
        var recipe = recipeRepository.findBestMatchByName(request.name()).orElse(null);
        if (recipe == null) {
            return new ToolResult.NotFound("a recipe matching \"" + request.name() + "\"");
        }

        Double newPer100gCalories = null;
        Double newPer100gProtein = null;
        Double newPer100gCarbs = null;
        Double newPer100gFat = null;
        if (request.totalWeightG() != null && request.totalCalories() != null) {
            double factor = 100.0 / request.totalWeightG();
            newPer100gCalories = request.totalCalories() * factor;
            newPer100gProtein = request.totalProteinG() != null ? request.totalProteinG() * factor : null;
            newPer100gCarbs = request.totalCarbsG() != null ? request.totalCarbsG() * factor : null;
            newPer100gFat = request.totalFatG() != null ? request.totalFatG() * factor : null;
        }

        var refined = recipe.refined(newPer100gCalories, newPer100gProtein, newPer100gCarbs, newPer100gFat,
                request.typicalServingG(), request.totalWeightG());
        recipeRepository.save(refined);

        return new ToolResult.Success(
                "Refined \"%s\": %.0f kcal, %.1fg protein, %.1fg carbs, %.1fg fat per 100g. No longer provisional."
                        .formatted(refined.name(), refined.per100gCalories(), refined.per100gProtein(),
                                refined.per100gCarbs(), refined.per100gFat()),
                refined);
    }
}
