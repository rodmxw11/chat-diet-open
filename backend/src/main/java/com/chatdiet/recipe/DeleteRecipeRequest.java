package com.chatdiet.recipe;

/** Request payload for {@link DeleteRecipeTool}: the (fuzzy) name of the recipe to delete. */
public record DeleteRecipeRequest(String name) {
}
