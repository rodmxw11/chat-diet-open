package com.chatdiet.shopping;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;

import java.time.LocalDateTime;

/**
 * Spring Data JDBC entity for a shopping list item, moving from {@link #STATUS_PENDING} to
 * {@link #STATUS_PURCHASED} via {@link #purchased}.
 *
 * @param foodItemId     the matched {@link com.chatdiet.fooditem.FoodItem}, if the item's
 *                       description matched a cached food; {@code null} otherwise
 * @param suggestedStore store suggested from purchase history at the time the item was added;
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
        LocalDateTime purchasedAt,
        Double estimatedCostUsd
) {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PURCHASED = "PURCHASED";

    @PersistenceCreator
    public ShoppingItem {
    }

    public ShoppingItem(String description, Long foodItemId, String suggestedStore, Double estimatedCostUsd) {
        this(null, description, foodItemId, suggestedStore, STATUS_PENDING, LocalDateTime.now(), null,
                estimatedCostUsd);
    }

    /**
     * Returns a copy of this item marked as purchased, recording the store and timestamp. If
     * {@code costUsd} is {@code null}, the item's prior {@code estimatedCostUsd} is kept as the
     * final cost.
     */
    public ShoppingItem purchased(String store, Double costUsd) {
        return new ShoppingItem(id, description, foodItemId, suggestedStore, STATUS_PURCHASED, addedAt,
                LocalDateTime.now(), costUsd != null ? costUsd : estimatedCostUsd);
    }
}
