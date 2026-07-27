package com.chatdiet.openfoodfacts;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
record OffApiResponse(int status, OffApiProduct product) {
}
