package com.chatdiet.shopping;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;

import java.time.LocalDateTime;

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

    public ShoppingItem purchased(String store, Double costUsd) {
        return new ShoppingItem(id, description, foodItemId, suggestedStore, STATUS_PURCHASED, addedAt,
                LocalDateTime.now(), costUsd != null ? costUsd : estimatedCostUsd);
    }
}
