package com.chatdiet.recipe;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;

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
