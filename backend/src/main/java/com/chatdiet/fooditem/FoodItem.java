package com.chatdiet.fooditem;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;

import java.time.LocalDateTime;

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

    public FoodItem withUsageBumped() {
        return new FoodItem(id, name, upc, per100gCalories, per100gProtein, per100gCarbs, per100gFat,
                typicalServingG, lookupSource, (useCount != null ? useCount : 0) + 1, LocalDateTime.now());
    }

    public ScaledNutrition scaledTo(double grams) {
        double factor = grams / 100.0;
        return new ScaledNutrition(
                (int) Math.round((per100gCalories != null ? per100gCalories : 0) * factor),
                (per100gProtein != null ? per100gProtein : 0) * factor,
                (per100gCarbs != null ? per100gCarbs : 0) * factor,
                (per100gFat != null ? per100gFat : 0) * factor
        );
    }

    public record ScaledNutrition(int calories, double proteinG, double carbsG, double fatG) {
    }
}
