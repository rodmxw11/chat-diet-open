package com.chatdiet.openfoodfacts;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
record OffApiProduct(
        @JsonProperty("product_name") String productName,
        OffApiNutriments nutriments,
        @JsonProperty("serving_quantity") Double servingQuantity
) {
}
