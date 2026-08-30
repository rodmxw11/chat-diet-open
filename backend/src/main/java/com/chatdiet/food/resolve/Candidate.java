package com.chatdiet.food.resolve;

import com.chatdiet.fdc.FdcCandidate;
import com.chatdiet.fooditem.FoodItem;

/**
 * One option in a clarification's numbered list - exactly one of {@code foodItemId}/{@code fdcId}
 * is set, unless {@code isEstimateOption}.
 */
public record Candidate(String label, Long foodItemId, Long fdcId, boolean isEstimateOption) {

    public static Candidate cached(FoodItem item) {
        return new Candidate(item.name(), item.id(), null, false);
    }

    public static Candidate fdc(FdcCandidate fdc) {
        return new Candidate(fdc.description(), null, fdc.fdcId(), false);
    }

    public static Candidate estimateOption() {
        return new Candidate("Estimate it yourself", null, null, true);
    }
}
