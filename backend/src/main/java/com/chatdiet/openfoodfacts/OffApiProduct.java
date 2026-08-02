package com.chatdiet.openfoodfacts;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Jackson mapping of the {@code product} object in an Open Food Facts API v2 response. */
@JsonIgnoreProperties(ignoreUnknown = true)
record OffApiProduct(
        @JsonProperty("product_name") String productName,
        OffApiNutriments nutriments,
        @JsonProperty("serving_quantity") Double servingQuantity
) {
}
