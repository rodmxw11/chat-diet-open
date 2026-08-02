package com.chatdiet.shopping;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.util.Optional;

public interface PurchaseHistoryRepository extends ListCrudRepository<PurchaseHistory, Long> {

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
