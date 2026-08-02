package com.chatdiet.shopping;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;

import java.time.LocalDateTime;

/**
 * Spring Data JDBC entity recording a single completed shopping purchase, used to derive
 * suggested stores for future shopping list items.
 *
 * @param foodItemId the matched {@link com.chatdiet.fooditem.FoodItem}, if the purchased item
 *                   corresponded to one; {@code null} otherwise
 * @param store      store the purchase was made at, if known; may be {@code null}
 * @param costUsd    actual or estimated cost of the purchase; may be {@code null}
 */
public record PurchaseHistory(
        @Id Long id,
        Long foodItemId,
        String description,
        String store,
        LocalDateTime purchasedAt,
        Double costUsd
) {

    @PersistenceCreator
    public PurchaseHistory {
    }

    public PurchaseHistory(Long foodItemId, String description, String store, Double costUsd) {
        this(null, foodItemId, description, store, LocalDateTime.now(), costUsd);
    }
}
