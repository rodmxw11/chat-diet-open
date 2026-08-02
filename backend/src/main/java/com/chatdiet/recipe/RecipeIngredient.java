package com.chatdiet.recipe;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;

/**
 * One ingredient line of a {@link Recipe}, recorded for reference rather than for automatic
 * nutrition computation.
 *
 * @param foodItemId         optional link to a matched food item; {@code null} when the
 *                           ingredient is only recorded as free text
 * @param quantityG          ingredient quantity in grams, or {@code null} when not tracked
 *                           precisely
 * @param variableIngredient {@code true} if the amount used varies between batches (e.g.
 *                           "to taste"), worth confirming with the user when the recipe is reused
 */
public record RecipeIngredient(
        @Id Long id,
        Long recipeId,
        Long foodItemId,
        String description,
        Double quantityG,
        Boolean variableIngredient
) {

    @PersistenceCreator
    public RecipeIngredient {
    }

    public RecipeIngredient(Long recipeId, String description, Boolean variableIngredient) {
        this(null, recipeId, null, description, null, variableIngredient);
    }
}
