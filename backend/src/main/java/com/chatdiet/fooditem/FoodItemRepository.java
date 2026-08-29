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
     * Not true fuzzy matching - just a bidirectional substring
     * match, since the model's phrasing rarely matches the cached product name verbatim
     * ("Coca-Cola can" vs. a cached "coca-cola"). Only matches active (non-deleted) items.
     */
    @Query("""
            SELECT * FROM food_item
            WHERE deleted_at IS NULL
              AND (LOWER(name) LIKE '%' || LOWER(:name) || '%'
                   OR LOWER(:name) LIKE '%' || LOWER(name) || '%')
            ORDER BY use_count DESC LIMIT 1
            """)
    Optional<FoodItem> findBestMatchByName(String name);

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
