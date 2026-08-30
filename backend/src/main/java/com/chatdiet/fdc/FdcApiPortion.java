package com.chatdiet.fdc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Jackson mapping of one entry in a FoodData Central food detail's {@code foodPortions} array. */
@JsonIgnoreProperties(ignoreUnknown = true)
record FdcApiPortion(Double gramWeight, String modifier) {
}
