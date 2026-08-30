package com.chatdiet.food;

/**
 * One food within a {@link LogFoodRequest}.
 *
 * @param foodRef            the food name as spoken/typed, verbatim
 * @param amountText         the amount phrase verbatim: {@code "142g"}, {@code "2"},
 *                           {@code "a bowl"}, {@code ""}. There is no separate grams field - a
 *                           model-supplied gram guess for a natural-count phrase denies the app a
 *                           real lookup it could otherwise do.
 * @param resolvedFoodItemId set on a follow-up call when the user picked a numbered candidate that
 *                           was a cached item (its id came from the prior clarification's list -
 *                           see {@link LogFoodTool})
 * @param resolvedFdcId      set on a follow-up call when the user picked a numbered candidate that
 *                           was an FDC result
 * @param useEstimate        set on a follow-up call when the user picked the "estimate it
 *                           yourself" option from a clarification list
 * @param totalCalories      estimated calories for this exact portion, used only when
 *                           {@code useEstimate} is set; null only if you genuinely cannot estimate
 * @param fiberG             estimated dietary fiber in grams
 * @param sugarG             estimated total sugar in grams
 * @param sodiumMg           estimated sodium in milligrams
 * @param saturatedFatG      estimated saturated fat in grams (a subset of totalFatG, not in addition to it)
 * @param cholesterolMg      estimated cholesterol in milligrams
 * @param potassiumMg        estimated potassium in milligrams
 */
public record LogFoodItemRequest(
        String foodRef,
        String amountText,
        Long resolvedFoodItemId,
        Long resolvedFdcId,
        Boolean useEstimate,
        Integer totalCalories,
        Double totalProteinG,
        Double totalCarbsG,
        Double totalFatG,
        Double fiberG,
        Double sugarG,
        Double sodiumMg,
        Double saturatedFatG,
        Double cholesterolMg,
        Double potassiumMg
) {
}
