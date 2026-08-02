package com.chatdiet.shopping;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;

public interface ShoppingItemRepository extends ListCrudRepository<ShoppingItem, Long> {

    @Query("SELECT * FROM shopping_item WHERE status = 'PENDING' ORDER BY added_at")
    List<ShoppingItem> findPending();

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
}
