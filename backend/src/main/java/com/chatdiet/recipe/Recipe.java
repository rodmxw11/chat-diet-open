package com.chatdiet.recipe;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;

import java.time.LocalDateTime;

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

    public Recipe withUsageBumped() {
        return new Recipe(id, name, per100gCalories, per100gProtein, per100gCarbs, per100gFat,
                typicalServingG, totalYieldG, provisional, (useCount != null ? useCount : 0) + 1,
                LocalDateTime.now());
    }

    /** Refinement is forward-only - it never touches past FOOD_ENTRY rows already logged. */
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
