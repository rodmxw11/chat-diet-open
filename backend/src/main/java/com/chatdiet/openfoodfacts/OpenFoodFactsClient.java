package com.chatdiet.openfoodfacts;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/** Looks up product nutrition data from the public Open Food Facts API by barcode (UPC). */
@Service
public class OpenFoodFactsClient {

    private final RestClient restClient = RestClient.create();

    /**
     * Looks up the given barcode against Open Food Facts.
     *
     * @param upc the product barcode
     * @return the product's nutrition info, or empty if the barcode is unknown, the product has
     *         no usable calorie data, or the request fails for any reason
     */
    public Optional<OffProduct> lookup(String upc) {
        try {
            var response = restClient.get()
                    .uri("https://world.openfoodfacts.org/api/v2/product/{barcode}.json", upc)
                    .retrieve()
                    .body(OffApiResponse.class);

            if (response == null || response.status() != 1 || response.product() == null) {
                return Optional.empty();
            }

            var product = response.product();
            var nutriments = product.nutriments();
            if (nutriments == null || nutriments.caloriesPer100g() == null) {
                // Product exists in OFF but has no usable nutrition data - treat like a miss
                // rather than caching/logging a false "0 calories".
                return Optional.empty();
            }

            return Optional.of(new OffProduct(
                    product.productName(),
                    nutriments.caloriesPer100g(),
                    nutriments.proteinPer100g(),
                    nutriments.carbsPer100g(),
                    nutriments.fatPer100g(),
                    nutriments.fiberPer100g(),
                    nutriments.sugarPer100g(),
                    gramsToMg(nutriments.sodiumGPer100g()),
                    nutriments.saturatedFatPer100g(),
                    gramsToMg(nutriments.cholesterolGPer100g()),
                    gramsToMg(nutriments.potassiumGPer100g()),
                    product.servingQuantity()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static Double gramsToMg(Double grams) {
        return grams != null ? grams * 1000 : null;
    }
}
