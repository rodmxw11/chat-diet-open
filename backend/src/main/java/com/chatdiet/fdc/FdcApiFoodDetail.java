package com.chatdiet.fdc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Jackson mapping of the FoodData Central single-food detail response ({@code /v1/food/{fdcId}}).
 * {@code servingSize}/{@code servingSizeUnit} are the label's reported serving - watch for
 * {@code servingSizeUnit: "ml"} on liquids, which {@link FdcNutrientMapper} declines to map into
 * a grams-based serving size rather than guess a density.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record FdcApiFoodDetail(
        Long fdcId,
        String description,
        List<FdcApiFoodNutrientDetail> foodNutrients,
        List<FdcApiPortion> foodPortions,
        Double servingSize,
        String servingSizeUnit
) {
}
