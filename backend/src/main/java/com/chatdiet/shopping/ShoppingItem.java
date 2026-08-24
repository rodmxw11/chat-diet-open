package com.chatdiet.shopping;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;

import java.time.LocalDateTime;

/**
 * Spring Data JDBC entity for a shopping list item, moving from {@link #STATUS_PENDING} to
 * {@link #STATUS_PURCHASED} via {@link #purchased} and back via {@link #pending}.
 *
 * @param foodItemId     the matched {@link com.chatdiet.fooditem.FoodItem}, if the item's
 *                       description matched a cached food; {@code null} otherwise
 * @param suggestedStore store suggested from prior purchases at the time the item was added;
 *                       may be {@code null} if no history matched
 * @param status         {@link #STATUS_PENDING} or {@link #STATUS_PURCHASED}
 * @param purchasedAt    when the item was marked purchased; {@code null} while pending
 */
public record ShoppingItem(
        @Id Long id,
        String description,
        Long foodItemId,
        String suggestedStore,
        String status,
        LocalDateTime addedAt,
        LocalDateTime purchasedAt
) {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PURCHASED = "PURCHASED";

    @PersistenceCreator
    public ShoppingItem {
    }

    public ShoppingItem(String description, Long foodItemId, String suggestedStore) {
        this(null, description, foodItemId, suggestedStore, STATUS_PENDING, LocalDateTime.now(), null);
    }

    /** Returns a copy of this item marked as purchased, recording the store and timestamp. */
    public ShoppingItem purchased(String store) {
        return new ShoppingItem(id, description, foodItemId, store != null ? store : suggestedStore,
                STATUS_PURCHASED, addedAt, LocalDateTime.now());
    }

    /** Returns a copy of this item reverted back to pending (the "uncheck" operation). */
    public ShoppingItem pending() {
        return new ShoppingItem(id, description, foodItemId, suggestedStore, STATUS_PENDING, addedAt, null);
    }
}
