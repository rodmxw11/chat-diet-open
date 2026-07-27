package com.chatdiet.recipe;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;

public interface RecipeIngredientRepository extends ListCrudRepository<RecipeIngredient, Long> {

    @Query("SELECT * FROM recipe_ingredient WHERE recipe_id = :recipeId")
    List<RecipeIngredient> findByRecipeId(Long recipeId);
}
