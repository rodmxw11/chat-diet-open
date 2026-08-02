package com.chatdiet.recipe;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;

import java.time.LocalDateTime;

/**
 * A saved, reusable recipe: nutrition normalized to per-100g so it can be scaled to any serving
 * size when logged.
 *
 * @param typicalServingG the recipe's usual serving size in grams, or {@code null} if unknown
 * @param totalYieldG     total weight in grams the recipe batch was computed from (e.g. the
 *                        pot-level weight, or a single weighed serving if that's all that was
 *                        available)
 * @param provisional     {@code true} if this recipe was created from a single serving's totals
 *                        rather than a confident whole-batch weighing, and hasn't been
 *                        {@link #refined} yet
 * @param useCount        number of times this recipe has been logged, starting at 1 on creation
 * @param lastUsedAt      timestamp of the most recent log or refinement
 */
public record Recipe(
        @Id Long id,
        String name,
        Double per100gCalories,
        Double per100gProtein,
        Double per100gCarbs,
        Double per100gFat,
        Double typicalServingG,
        Double totalYieldG,
        Boolean provisional,
        Integer useCount,
        LocalDateTime lastUsedAt
) {

    @PersistenceCreator
    public Recipe {
    }

    public Recipe(String name, Double per100gCalories, Double per100gProtein, Double per100gCarbs,
                  Double per100gFat, Double typicalServingG, Double totalYieldG, Boolean provisional) {
        this(null, name, per100gCalories, per100gProtein, per100gCarbs, per100gFat, typicalServingG,
                totalYieldG, provisional, 1, LocalDateTime.now());
    }

    /**
     * Returns a copy with the use count incremented and {@code lastUsedAt} refreshed to now,
     * recording that this recipe was logged again.
     */
    public Recipe withUsageBumped() {
        return new Recipe(id, name, per100gCalories, per100gProtein, per100gCarbs, per100gFat,
                typicalServingG, totalYieldG, provisional, (useCount != null ? useCount : 0) + 1,
                LocalDateTime.now());
    }

    /**
     * Returns a copy with updated per-100g nutrition and serving/yield info, clearing the
     * {@code provisional} flag. Any {@code new*} argument left {@code null} keeps its current
     * value. Refinement is forward-only - it never touches past FOOD_ENTRY rows already logged.
     */
    public Recipe refined(Double newPer100gCalories, Double newPer100gProtein, Double newPer100gCarbs,
                           Double newPer100gFat, Double newTypicalServingG, Double newTotalYieldG) {
        return new Recipe(id, name,
                newPer100gCalories != null ? newPer100gCalories : per100gCalories,
                newPer100gProtein != null ? newPer100gProtein : per100gProtein,
                newPer100gCarbs != null ? newPer100gCarbs : per100gCarbs,
                newPer100gFat != null ? newPer100gFat : per100gFat,
                newTypicalServingG != null ? newTypicalServingG : typicalServingG,
                newTotalYieldG != null ? newTotalYieldG : totalYieldG,
                false, useCount, lastUsedAt);
    }
}
