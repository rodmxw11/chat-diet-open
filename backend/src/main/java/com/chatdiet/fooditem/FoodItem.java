package com.chatdiet.fooditem;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;

import java.time.LocalDateTime;

/**
 * Spring Data JDBC entity caching a food product's per-100g nutrition profile, keyed by barcode
 * and/or name, so repeated lookups (UPC scan or name match) don't require re-querying an
 * external source.
 *
 * @param upc            product barcode; may be {@code null} for items added without a scan
 * @param typicalServingG Open Food Facts' (or the source's) reported serving size in grams; may be {@code null}
 * @param lookupSource   where this item's nutrition data came from (e.g. an Open Food Facts lookup or a manual entry)
 * @param useCount       number of times this cached item has been used to log a food entry
 * @param lastUsedAt     timestamp of the most recent use, or {@code null} if never used
 */
public record FoodItem(
        @Id Long id,
        String name,
        String upc,
        Double per100gCalories,
        Double per100gProtein,
        Double per100gCarbs,
        Double per100gFat,
        Double typicalServingG,
        String lookupSource,
        Integer useCount,
        LocalDateTime lastUsedAt
) {

    @PersistenceCreator
    public FoodItem {
    }

    public FoodItem(String name, String upc, Double per100gCalories, Double per100gProtein,
                     Double per100gCarbs, Double per100gFat, Double typicalServingG, String lookupSource) {
        this(null, name, upc, per100gCalories, per100gProtein, per100gCarbs, per100gFat,
                typicalServingG, lookupSource, 0, null);
    }

    /** Returns a copy of this item with {@code useCount} incremented and {@code lastUsedAt} set to now. */
    public FoodItem withUsageBumped() {
        return new FoodItem(id, name, upc, per100gCalories, per100gProtein, per100gCarbs, per100gFat,
                typicalServingG, lookupSource, (useCount != null ? useCount : 0) + 1, LocalDateTime.now());
    }

    /** Scales this item's per-100g nutrition profile to the given portion size in grams. */
    public ScaledNutrition scaledTo(double grams) {
        double factor = grams / 100.0;
        return new ScaledNutrition(
                (int) Math.round((per100gCalories != null ? per100gCalories : 0) * factor),
                (per100gProtein != null ? per100gProtein : 0) * factor,
                (per100gCarbs != null ? per100gCarbs : 0) * factor,
                (per100gFat != null ? per100gFat : 0) * factor
        );
    }

    /** Nutrition values for a specific portion size, derived from {@link #scaledTo(double)}. */
    public record ScaledNutrition(int calories, double proteinG, double carbsG, double fatG) {
    }
}
