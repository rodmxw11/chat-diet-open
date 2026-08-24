package com.chatdiet.fooditem;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;

/** Spring Data JDBC repository for {@link FoodItem}. */
public interface FoodItemRepository extends ListCrudRepository<FoodItem, Long> {

    /** Returns the cached food item with the given barcode, if any. */
    @Query("SELECT * FROM food_item WHERE upc = :upc")
    Optional<FoodItem> findByUpc(String upc);

    /** All cached food items, most-used first, for the shopping-list picker. */
    @Query("SELECT * FROM food_item ORDER BY use_count DESC, name")
    List<FoodItem> findAllOrderByUseCountDescNameAsc();

    /**
     * Not true fuzzy matching - just a bidirectional substring
     * match, since the model's phrasing rarely matches the cached product name verbatim
     * ("Coca-Cola can" vs. a cached "coca-cola").
     */
    @Query("""
            SELECT * FROM food_item
            WHERE LOWER(name) LIKE '%' || LOWER(:name) || '%'
               OR LOWER(:name) LIKE '%' || LOWER(name) || '%'
            ORDER BY use_count DESC LIMIT 1
            """)
    Optional<FoodItem> findBestMatchByName(String name);
}
