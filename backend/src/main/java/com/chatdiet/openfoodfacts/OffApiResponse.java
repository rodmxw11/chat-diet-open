package com.chatdiet.openfoodfacts;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Jackson mapping of the top-level Open Food Facts API v2 product-lookup response.
 *
 * @param status 1 if the product was found, 0 (or any non-1 value) if the barcode is unknown to
 *               Open Food Facts
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record OffApiResponse(int status, OffApiProduct product) {
}
