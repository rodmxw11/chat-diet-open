package com.chatdiet.fdc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Jackson mapping of one entry in a FoodData Central food detail's {@code foodNutrients} array -
 * nests the nutrient's identity under a {@code nutrient} object, unlike the flatter search-response
 * shape ({@code foodNutrients[].nutrientId} there vs. {@code foodNutrients[].nutrient.id} here).
 * Code written against one shape reads {@code null} against the other silently, so only this
 * detail shape is parsed for nutrition - see {@link FdcNutrientMapper}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record FdcApiFoodNutrientDetail(NutrientRef nutrient, Double amount) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record NutrientRef(Integer id, String number, String name, String unitName) {
    }
}
