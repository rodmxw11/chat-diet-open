package com.chatdiet.recipe;

import java.util.List;

public record CreateRecipeRequest(
        String name,
        double totalWeightG,
        double totalCalories,
        double totalProteinG,
        double totalCarbsG,
        double totalFatG,
        Double typicalServingG,
        boolean provisional,
        List<RecipeIngredientInput> ingredients
) {
}
