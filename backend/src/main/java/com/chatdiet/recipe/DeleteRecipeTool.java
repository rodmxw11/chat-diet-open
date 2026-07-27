package com.chatdiet.recipe;

import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
@IntentTool(
        name = "delete_recipe",
        intents = {"recipe_intake"},
        description = "Delete a recipe by name, e.g. when the user confirms a stale or unwanted recipe should be removed."
)
public class DeleteRecipeTool implements Function<DeleteRecipeRequest, ToolResult> {

    private final RecipeRepository recipeRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;

    public DeleteRecipeTool(RecipeRepository recipeRepository, RecipeIngredientRepository recipeIngredientRepository) {
        this.recipeRepository = recipeRepository;
        this.recipeIngredientRepository = recipeIngredientRepository;
    }

    @Override
    public ToolResult apply(DeleteRecipeRequest request) {
        var recipe = recipeRepository.findBestMatchByName(request.name()).orElse(null);
        if (recipe == null) {
            return new ToolResult.NotFound("a recipe matching \"" + request.name() + "\"");
        }

        recipeIngredientRepository.deleteAll(recipeIngredientRepository.findByRecipeId(recipe.id()));
        recipeRepository.deleteById(recipe.id());

        return new ToolResult.Success("Deleted recipe \"" + recipe.name() + "\".", null);
    }
}
