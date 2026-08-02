package com.chatdiet.shopping;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;

import java.time.LocalDateTime;

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
