package com.chatdiet.shopping;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;

/** Spring Data JDBC repository for {@link ShoppingItem} records. */
public interface ShoppingItemRepository extends ListCrudRepository<ShoppingItem, Long> {

    /** Returns all pending (not yet purchased) items, oldest first. */
    @Query("SELECT * FROM shopping_item WHERE status = 'PENDING' ORDER BY added_at")
    List<ShoppingItem> findPending();

    /** Returns all items, pending group first (each chronological), then purchased group (each chronological). */
    @Query("""
            SELECT * FROM shopping_item
            ORDER BY CASE status WHEN 'PENDING' THEN 0 ELSE 1 END, added_at
            """)
    List<ShoppingItem> findAllOrderedByStatusThenAddedAt();

    /**
     * Bidirectional substring match against pending items, same pattern as
     * FoodItemRepository.findBestMatchByName - the model's phrasing rarely matches the
     * originally-added description verbatim.
     */
    @Query("""
            SELECT * FROM shopping_item
            WHERE status = 'PENDING'
              AND (LOWER(description) LIKE '%' || LOWER(:description) || '%'
                   OR LOWER(:description) LIKE '%' || LOWER(description) || '%')
            ORDER BY added_at DESC LIMIT 1
            """)
    Optional<ShoppingItem> findBestPendingMatchByDescription(String description);

    /**
     * Bidirectional substring match against purchased items, used by revert to find the item to
     * un-mark.
     */
    @Query("""
            SELECT * FROM shopping_item
            WHERE status = 'PURCHASED'
              AND (LOWER(description) LIKE '%' || LOWER(:description) || '%'
                   OR LOWER(:description) LIKE '%' || LOWER(description) || '%')
            ORDER BY purchased_at DESC LIMIT 1
            """)
    Optional<ShoppingItem> findBestPurchasedMatchByDescription(String description);

    /**
     * Returns the store most frequently associated with prior shopping-list items matched to the
     * given food item, or empty if none recorded a store. Replaces the old purchase_history
     * lookup now that shopping_item itself is the history.
     */
    @Query("""
            SELECT suggested_store FROM shopping_item
            WHERE food_item_id = :foodItemId AND suggested_store IS NOT NULL
            GROUP BY suggested_store ORDER BY COUNT(*) DESC LIMIT 1
            """)
    Optional<String> findMostCommonStoreByFoodItemId(Long foodItemId);

    /**
     * Returns the store most frequently associated with prior shopping-list items whose
     * description bidirectionally substring-matches (case-insensitive) the given description, or
     * empty if none match or none recorded a store.
     */
    @Query("""
            SELECT suggested_store FROM shopping_item
            WHERE suggested_store IS NOT NULL
              AND (LOWER(description) LIKE '%' || LOWER(:description) || '%'
                   OR LOWER(:description) LIKE '%' || LOWER(description) || '%')
            GROUP BY suggested_store ORDER BY COUNT(*) DESC LIMIT 1
            """)
    Optional<String> findMostCommonStoreByDescription(String description);
}
