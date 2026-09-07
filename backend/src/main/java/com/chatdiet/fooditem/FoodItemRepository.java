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
     * Every row carrying this barcode, soft-deleted ones included - the upsert path needs to see
     * a deleted row so a rescan revives it in place instead of colliding with the unique
     * {@code upc} index.
     */
    @Query("SELECT * FROM food_item WHERE upc = :upc")
    List<FoodItem> findAllByUpcIncludingDeleted(String upc);

    /**
     * Search for the FOOD_ITEM CRUD page: matches on a name or alias substring (blank
     * {@code query} matches everything), active-only unless {@code includeDeleted}. Aliases are
     * stored already lowercase-normalized, so only the query side needs lowering there.
     */
    @Query("""
            SELECT * FROM food_item
            WHERE (:includeDeleted OR deleted_at IS NULL)
              AND (LOWER(name) LIKE '%' || LOWER(:query) || '%'
                   OR EXISTS (SELECT 1 FROM food_alias fa
                              WHERE fa.food_item_id = food_item.id
                                AND fa.alias_normalized LIKE '%' || LOWER(:query) || '%'))
            ORDER BY name
            """)
    List<FoodItem> search(String query, boolean includeDeleted);
}
