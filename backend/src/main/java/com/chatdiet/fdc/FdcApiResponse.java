package com.chatdiet.fdc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Jackson mapping of the top-level FoodData Central {@code /v1/foods/search} response. */
@JsonIgnoreProperties(ignoreUnknown = true)
record FdcApiResponse(List<FdcApiFood> foods) {
}
