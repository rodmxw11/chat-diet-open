package com.chatdiet.recipe;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;

/** Spring Data JDBC repository for {@link RecipeIngredient}. */
public interface RecipeIngredientRepository extends ListCrudRepository<RecipeIngredient, Long> {

    /** Returns all ingredient lines belonging to the given recipe. */
    @Query("SELECT * FROM recipe_ingredient WHERE recipe_id = :recipeId")
    List<RecipeIngredient> findByRecipeId(Long recipeId);
}
