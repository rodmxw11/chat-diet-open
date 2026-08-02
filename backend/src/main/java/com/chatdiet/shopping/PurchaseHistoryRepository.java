package com.chatdiet.shopping;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Spring Data JDBC repository for {@link PurchaseHistory} records. */
public interface PurchaseHistoryRepository extends ListCrudRepository<PurchaseHistory, Long> {

    /** Returns all purchases recorded within {@code [start, end)}. */
    @Query("SELECT * FROM purchase_history WHERE purchased_at >= :start AND purchased_at < :end")
    List<PurchaseHistory> findByPurchasedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Returns the store most frequently associated with purchases of the given food item, or
     * empty if no purchase of it recorded a store.
     */
    @Query("""
            SELECT store FROM purchase_history
            WHERE food_item_id = :foodItemId AND store IS NOT NULL
            GROUP BY store ORDER BY COUNT(*) DESC LIMIT 1
            """)
    Optional<String> findMostCommonStoreByFoodItemId(Long foodItemId);

    /**
     * Returns the store most frequently associated with purchases whose description
     * bidirectionally substring-matches (case-insensitive) the given description, or empty if
     * none match or none recorded a store.
     */
    @Query("""
            SELECT store FROM purchase_history
            WHERE store IS NOT NULL
              AND (LOWER(description) LIKE '%' || LOWER(:description) || '%'
                   OR LOWER(:description) LIKE '%' || LOWER(description) || '%')
            GROUP BY store ORDER BY COUNT(*) DESC LIMIT 1
            """)
    Optional<String> findMostCommonStoreByDescription(String description);
}
