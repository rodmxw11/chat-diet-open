package com.chatdiet.recipe;

/**
 * Ingredient line supplied by the model when creating a recipe via {@link CreateRecipeTool}.
 *
 * @param variable {@code true} if the amount used varies between batches (e.g. "to taste"),
 *                 stored as {@link RecipeIngredient#variableIngredient()}
 */
public record RecipeIngredientInput(String description, Boolean variable) {
}
