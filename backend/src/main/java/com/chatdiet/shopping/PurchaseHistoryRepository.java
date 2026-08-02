package com.chatdiet.shopping;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PurchaseHistoryRepository extends ListCrudRepository<PurchaseHistory, Long> {

    @Query("SELECT * FROM purchase_history WHERE purchased_at >= :start AND purchased_at < :end")
    List<PurchaseHistory> findByPurchasedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("""
            SELECT store FROM purchase_history
            WHERE food_item_id = :foodItemId AND store IS NOT NULL
            GROUP BY store ORDER BY COUNT(*) DESC LIMIT 1
            """)
    Optional<String> findMostCommonStoreByFoodItemId(Long foodItemId);

    @Query("""
            SELECT store FROM purchase_history
            WHERE store IS NOT NULL
              AND (LOWER(description) LIKE '%' || LOWER(:description) || '%'
                   OR LOWER(:description) LIKE '%' || LOWER(description) || '%')
            GROUP BY store ORDER BY COUNT(*) DESC LIMIT 1
            """)
    Optional<String> findMostCommonStoreByDescription(String description);
}
