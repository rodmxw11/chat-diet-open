package com.chatdiet.fdc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Jackson mapping of one food in a FoodData Central search response's {@code foods} array -
 * lightweight, no nutrition. The search endpoint's nutrient shape differs from the detail
 * endpoint's and isn't read here; fetch a selected candidate's nutrition via
 * {@link FdcClient#fetchDetail}. {@code gtinUpc} is present on Branded results only, used to
 * verify a UPC lookup ({@link FdcClient#lookupBrandedByUpc}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record FdcApiFood(Long fdcId, String description, String gtinUpc) {
}
