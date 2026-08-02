package com.chatdiet.recipe;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Spring Data JDBC repository for {@link Recipe}. */
public interface RecipeRepository extends ListCrudRepository<Recipe, Long> {

    /**
     * Finds the most-used recipe whose name matches {@code name} in either direction.
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

    /**
     * Returns recipes logged at most once (never reused) and not touched since {@code cutoff} -
     * candidates for {@link ListStaleRecipesTool} to suggest deleting.
     */
    @Query("SELECT * FROM recipe WHERE use_count <= 1 AND last_used_at < :cutoff")
    List<Recipe> findStale(LocalDateTime cutoff);
}
