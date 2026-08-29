package com.chatdiet.fdc;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Looks up raw/generic food nutrition data from USDA FoodData Central, restricted to the
 * Foundation Foods and SR Legacy data types (analytically-sourced, unbranded foods) - branded
 * packaged products are Open Food Facts' job, via UPC.
 *
 * <p>Requires a free api.data.gov key ({@code chat-diet.fdc.api-key}). If unset, {@link #search}
 * always returns empty so the food-lookup tier this feeds just falls through to the next one
 * rather than failing.
 */
@Service
public class FdcClient {

    private final RestClient restClient = RestClient.create();
    private final String apiKey;

    public FdcClient(@Value("${chat-diet.fdc.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * Searches FoodData Central for the given food name and returns the best match, if any is a
     * plausible match for the query - not just FDC's top-ranked result regardless of relevance.
     *
     * @param query the food name to search for
     * @return the best match's nutrition info, or empty if no key is configured, nothing came
     *         back, or nothing returned was a plausible match for the query
     */
    public Optional<FdcProduct> search(String query) {
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }

        try {
            var response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https").host("api.nal.usda.gov").path("/fdc/v1/foods/search")
                            .queryParam("api_key", apiKey)
                            .queryParam("query", query)
                            .queryParam("dataType", "Foundation", "SR Legacy")
                            .queryParam("pageSize", 10)
                            .build())
                    .retrieve()
                    .body(FdcApiResponse.class);

            if (response == null || response.foods() == null) {
                return Optional.empty();
            }

            return response.foods().stream()
                    .filter(food -> isPlausibleMatch(query, food.description()))
                    .findFirst()
                    .flatMap(this::toProduct);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Same bidirectional-substring test {@link com.chatdiet.fooditem.FoodItemRepository}'s cache
     * lookup already uses - a plain FDC search hit isn't trustworthy enough to cache and log
     * against unmoderated (e.g. searching "yogurt" and getting back an oddly-specific outlier
     * row); requiring the query and the description to at least contain one another keeps this
     * to genuinely-relevant matches without needing a fuzzy-matching library.
     */
    static boolean isPlausibleMatch(String query, String description) {
        if (description == null) return false;
        var q = query.toLowerCase(Locale.ROOT).trim();
        var d = description.toLowerCase(Locale.ROOT).trim();
        return d.contains(q) || q.contains(d);
    }

    Optional<FdcProduct> toProduct(FdcApiFood food) {
        List<FdcApiNutrient> nutrients = food.foodNutrients();
        var calories = FdcNutrientMapping.extract(nutrients, FdcNutrientMapping.CALORIES);
        if (calories == null) {
            // No usable calorie data - treat like a miss rather than caching/logging a false "0".
            return Optional.empty();
        }

        return Optional.of(new FdcProduct(
                food.description(),
                calories,
                FdcNutrientMapping.extract(nutrients, FdcNutrientMapping.PROTEIN),
                FdcNutrientMapping.extract(nutrients, FdcNutrientMapping.CARBS),
                FdcNutrientMapping.extract(nutrients, FdcNutrientMapping.FAT),
                FdcNutrientMapping.extract(nutrients, FdcNutrientMapping.FIBER),
                FdcNutrientMapping.extract(nutrients, FdcNutrientMapping.SUGAR),
                FdcNutrientMapping.extract(nutrients, FdcNutrientMapping.SODIUM_MG),
                FdcNutrientMapping.extract(nutrients, FdcNutrientMapping.SATURATED_FAT),
                FdcNutrientMapping.extract(nutrients, FdcNutrientMapping.CHOLESTEROL_MG),
                FdcNutrientMapping.extract(nutrients, FdcNutrientMapping.POTASSIUM_MG),
                null));
    }
}
