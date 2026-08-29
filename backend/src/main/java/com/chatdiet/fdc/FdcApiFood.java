package com.chatdiet.fdc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Jackson mapping of one food in a FoodData Central search response's {@code foods} array. */
@JsonIgnoreProperties(ignoreUnknown = true)
record FdcApiFood(String description, List<FdcApiNutrient> foodNutrients) {
}
