package com.chatdiet.recipe;

/** Request payload for {@link FindRecipeTool}: the (fuzzy) recipe name to look up. */
public record FindRecipeRequest(String name) {
}
