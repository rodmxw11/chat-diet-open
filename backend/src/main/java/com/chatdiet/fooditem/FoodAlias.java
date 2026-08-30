package com.chatdiet.fooditem;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;

import java.time.LocalDateTime;

/**
 * Spring Data JDBC entity for an exact-match name a food is known by. Resolution against this
 * table is exact-only ({@code WHERE alias_normalized = ?}) - no {@code LIKE}, no ranking, no
 * threshold. This is the only path that writes a {@link FoodItem} row without asking, so it must
 * be incapable of guessing.
 *
 * @param aliasNormalized the normalized lookup key ({@link FoodAliasNormalizer#normalize}), unique
 *                         across the table - two different foods can never share one
 * @param foodItemId      the {@link FoodItem} this alias resolves to
 * @param source           where this alias came from: {@code USER} (a clarification turn),
 *                         {@code OFF}, {@code FDC}, or {@code MANUAL} (a Food Items save)
 */
public record FoodAlias(
        @Id Long id,
        String aliasNormalized,
        Long foodItemId,
        String source,
        LocalDateTime createdAt
) {

    @PersistenceCreator
    public FoodAlias {
    }

    public FoodAlias(String aliasNormalized, Long foodItemId, String source) {
        this(null, aliasNormalized, foodItemId, source, LocalDateTime.now());
    }
}
