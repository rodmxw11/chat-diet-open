package com.chatdiet.fooditem;

/**
 * Request body for creating or editing a {@link FoodItem} from the food-items management page.
 * {@code name} and {@code per100gCalories} are the only required fields; everything else may be
 * left unset if unknown.
 */
public record FoodItemUpsertRequest(
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
        String lookupSource
) {
}
