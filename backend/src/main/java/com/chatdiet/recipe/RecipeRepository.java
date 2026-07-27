package com.chatdiet.recipe;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RecipeRepository extends ListCrudRepository<Recipe, Long> {

    /**
     * Not true fuzzy matching (that's a future refinement) - a bidirectional substring match,
     * same pattern as FoodItemRepository.findBestMatchByName.
     */
    @Query("""
            SELECT * FROM recipe
            WHERE LOWER(name) LIKE '%' || LOWER(:name) || '%'
               OR LOWER(:name) LIKE '%' || LOWER(name) || '%'
            ORDER BY use_count DESC LIMIT 1
            """)
    Optional<Recipe> findBestMatchByName(String name);

    @Query("SELECT * FROM recipe WHERE use_count <= 1 AND last_used_at < :cutoff")
    List<Recipe> findStale(LocalDateTime cutoff);
}
