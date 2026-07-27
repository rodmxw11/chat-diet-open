package com.chatdiet.fooditem;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.util.Optional;

public interface FoodItemRepository extends ListCrudRepository<FoodItem, Long> {

    @Query("SELECT * FROM food_item WHERE upc = :upc")
    Optional<FoodItem> findByUpc(String upc);

    /**
     * Not true fuzzy matching (that's recipe intake territory) - just a bidirectional substring
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
