package com.chatdiet.fdc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Jackson mapping of the FoodData Central single-food detail response ({@code /v1/food/{fdcId}}). */
@JsonIgnoreProperties(ignoreUnknown = true)
record FdcApiFoodDetail(List<FdcApiPortion> foodPortions) {
}
