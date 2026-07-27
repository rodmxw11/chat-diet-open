package com.chatdiet.recipe;

import com.chatdiet.intent.IntentTool;
import com.chatdiet.intent.ToolResult;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@IntentTool(
        name = "list_stale_recipes",
        intents = {"recipe_intake"},
        description = "List recipes that were logged once and never reused, as candidates to prompt the user to delete."
)
public class ListStaleRecipesTool implements Function<ListStaleRecipesRequest, ToolResult> {

    private static final int STALE_AFTER_DAYS = 30;

    private final RecipeRepository recipeRepository;

    public ListStaleRecipesTool(RecipeRepository recipeRepository) {
        this.recipeRepository = recipeRepository;
    }

    @Override
    public ToolResult apply(ListStaleRecipesRequest request) {
        var stale = recipeRepository.findStale(LocalDateTime.now().minusDays(STALE_AFTER_DAYS));
        if (stale.isEmpty()) {
            return new ToolResult.Success("No stale recipes.", stale);
        }

        var names = stale.stream().map(Recipe::name).collect(Collectors.joining(", "));
        return new ToolResult.Success("Logged once, never reused: " + names + ". Want to delete any of these?", stale);
    }
}
