package com.chatdiet.fooditem;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;

/** Spring Data JDBC repository for {@link FoodItem}. */
public interface FoodItemRepository extends ListCrudRepository<FoodItem, Long> {

    /** Returns the cached, active food item with the given barcode, if any. */
    @Query("SELECT * FROM food_item WHERE upc = :upc AND deleted_at IS NULL")
    Optional<FoodItem> findByUpc(String upc);

    /**
     * Not true fuzzy matching - a bidirectional substring match, since the model's phrasing
     * rarely matches the cached product name verbatim ("Coca-Cola can" vs. a cached
     * "coca-cola"). Filtered down to candidates that aren't just a substantially longer,
     * different food that happens to share a word - a bare "chicken" would otherwise
     * substring-match a cached "chicken salad sandwich" and silently scale by its (very
     * different) per-100g values. A wordier query matching a plainer cached name is still fine
     * (that's the Coca-Cola case); only a candidate more than one word longer than the query is
     * rejected. {@code use_count DESC} among the survivors picks the most-established match,
     * same as before this filter existed. Only matches active (non-deleted) items.
     */
    default Optional<FoodItem> findBestMatchByName(String name) {
        return candidateMatchesByName(name).stream()
                .filter(item -> wordCount(item.name()) - wordCount(name) <= 1)
                .findFirst();
    }

    @Query("""
            SELECT * FROM food_item
            WHERE deleted_at IS NULL
              AND (LOWER(name) LIKE '%' || LOWER(:name) || '%'
                   OR LOWER(:name) LIKE '%' || LOWER(name) || '%')
            ORDER BY use_count DESC
            """)
    List<FoodItem> candidateMatchesByName(String name);

    private static int wordCount(String text) {
        var trimmed = text.trim();
        return trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
    }

    /**
     * Search for the FOOD_ITEM CRUD page: matches on a name substring (blank {@code query}
     * matches everything), active-only unless {@code includeDeleted}.
     */
    @Query("""
            SELECT * FROM food_item
            WHERE (:includeDeleted OR deleted_at IS NULL)
              AND LOWER(name) LIKE '%' || LOWER(:query) || '%'
            ORDER BY name
            """)
    List<FoodItem> search(String query, boolean includeDeleted);
}
