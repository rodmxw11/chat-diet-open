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
 * @param deletedAt      soft-delete timestamp, or {@code null} while active. A deleted item stops
 *                       surfacing for new UPC/name matches but is left in place for past entries
 *                       and history to keep resolving against.
 */
public record FoodItem(
        @Id Long id,
        String name,
        String upc,
        Double per100gCalories,
        Double per100gProtein,
        Double per100gCarbs,
        Double per100gFat,
        Double per100gFiber,
        Double per100gSugar,
        Double per100gSodiumMg,
        Double per100gSaturatedFat,
        Double per100gCholesterolMg,
        Double per100gPotassiumMg,
        Double typicalServingG,
        String lookupSource,
        Integer useCount,
        LocalDateTime lastUsedAt,
        LocalDateTime deletedAt
) {

    @PersistenceCreator
    public FoodItem {
    }

    public FoodItem(String name, String upc, Double per100gCalories, Double per100gProtein,
                     Double per100gCarbs, Double per100gFat, Double per100gFiber, Double per100gSugar,
                     Double per100gSodiumMg, Double per100gSaturatedFat, Double per100gCholesterolMg,
                     Double per100gPotassiumMg, Double typicalServingG, String lookupSource) {
        this(null, name, upc, per100gCalories, per100gProtein, per100gCarbs, per100gFat,
                per100gFiber, per100gSugar, per100gSodiumMg, per100gSaturatedFat, per100gCholesterolMg,
                per100gPotassiumMg, typicalServingG, lookupSource, 0, null, null);
    }

    /** Returns a copy of this item with {@code useCount} incremented and {@code lastUsedAt} set to now. */
    public FoodItem withUsageBumped() {
        return new FoodItem(id, name, upc, per100gCalories, per100gProtein, per100gCarbs, per100gFat,
                per100gFiber, per100gSugar, per100gSodiumMg, per100gSaturatedFat, per100gCholesterolMg,
                per100gPotassiumMg, typicalServingG, lookupSource, (useCount != null ? useCount : 0) + 1,
                LocalDateTime.now(), deletedAt);
    }

    /** Returns a copy of this item soft-deleted (stamped with the current time). */
    public FoodItem withDeleted() {
        return new FoodItem(id, name, upc, per100gCalories, per100gProtein, per100gCarbs, per100gFat,
                per100gFiber, per100gSugar, per100gSodiumMg, per100gSaturatedFat, per100gCholesterolMg,
                per100gPotassiumMg, typicalServingG, lookupSource, useCount, lastUsedAt, LocalDateTime.now());
    }

    /** Returns a copy of this item restored (un-deleted). */
    public FoodItem withRestored() {
        return new FoodItem(id, name, upc, per100gCalories, per100gProtein, per100gCarbs, per100gFat,
                per100gFiber, per100gSugar, per100gSodiumMg, per100gSaturatedFat, per100gCholesterolMg,
                per100gPotassiumMg, typicalServingG, lookupSource, useCount, lastUsedAt, null);
    }

    /** Scales this item's per-100g nutrition profile to the given portion size in grams. */
    public ScaledNutrition scaledTo(double grams) {
        double factor = grams / 100.0;
        return new ScaledNutrition(
                (int) Math.round((per100gCalories != null ? per100gCalories : 0) * factor),
                (per100gProtein != null ? per100gProtein : 0) * factor,
                (per100gCarbs != null ? per100gCarbs : 0) * factor,
                (per100gFat != null ? per100gFat : 0) * factor,
                per100gFiber != null ? per100gFiber * factor : null,
                per100gSugar != null ? per100gSugar * factor : null,
                per100gSodiumMg != null ? per100gSodiumMg * factor : null,
                per100gSaturatedFat != null ? per100gSaturatedFat * factor : null,
                per100gCholesterolMg != null ? per100gCholesterolMg * factor : null,
                per100gPotassiumMg != null ? per100gPotassiumMg * factor : null
        );
    }

    /**
     * Nutrition values for a specific portion size, derived from {@link #scaledTo(double)}.
     * Micronutrient fields are nullable since Open Food Facts' per-product completeness varies.
     */
    public record ScaledNutrition(int calories, double proteinG, double carbsG, double fatG,
                                   Double fiberG, Double sugarG, Double sodiumMg, Double saturatedFatG,
                                   Double cholesterolMg, Double potassiumMg) {
    }
}
