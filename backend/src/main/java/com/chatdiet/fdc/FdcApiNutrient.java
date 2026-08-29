package com.chatdiet.fdc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Jackson mapping of one entry in a FoodData Central food's {@code foodNutrients} array.
 *
 * @param nutrientId the FDC-internal nutrient ID (stable across Foundation Foods/SR Legacy,
 *                   unlike the legacy {@code nutrientNumber} field) - see {@link FdcNutrientMapping}
 * @param value      the amount, per 100g of the food, in the nutrient's native unit
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record FdcApiNutrient(Integer nutrientId, Double value) {
}
