package com.chatdiet.fdc;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Comparator;
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
     * plausible match for the query - not just FDC's top-ranked result regardless of relevance,
     * and preferring the plainest (fewest-qualifier) match rather than FDC's own relevance order
     * (which for an unqualified name like "banana" can rank a processed variant like "Bananas,
     * dehydrated, or banana powder" ahead of "Bananas, raw"). Used by the automated food-logging
     * flow, where there's no person present to pick between several candidates; see
     * {@link #searchCandidates} for that case.
     *
     * @param query the food name to search for
     * @return the best match's nutrition info, or empty if no key is configured, nothing came
     *         back, or nothing returned was a plausible match for the query
     */
    public Optional<FdcProduct> search(String query) {
        return searchCandidates(query, 1).stream().findFirst();
    }

    /**
     * Like {@link #search}, but returns up to {@code limit} plausible matches instead of just the
     * best one - for the food-item form, where a person is present to pick the right one out of
     * several (e.g. "canned beans" -> black/kidney/pinto/...).
     */
    public List<FdcProduct> searchCandidates(String query, int limit) {
        if (apiKey == null || apiKey.isBlank()) {
            return List.of();
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
                return List.of();
            }

            return response.foods().stream()
                    .filter(food -> isPlausibleMatch(query, food.description()))
                    .sorted(Comparator.comparingInt(food -> qualifierCount(food.description())))
                    .map(this::toProduct)
                    .flatMap(Optional::stream)
                    .limit(limit)
                    .toList();
        } catch (Exception e) {
            return List.of();
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

    /**
     * Rough measure of how qualified/processed a description is, by counting its comma- and
     * whitespace-separated tokens (e.g. "Bananas, raw" -> 2, "Bananas, dehydrated, or banana
     * powder" -> 5) - used to sort plausible matches so the plainest one is picked first, instead
     * of trusting FDC's own relevance ranking to have put the generic form ahead of a processed
     * or branded variant. Not real NLP, just a cheap proxy: more description = more qualifiers.
     */
    static int qualifierCount(String description) {
        if (description == null || description.isBlank()) {
            return Integer.MAX_VALUE;
        }
        return description.split("[,\\s]+").length;
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
                null,
                food.fdcId()));
    }

    /**
     * Fetches the real per-unit portion weights USDA reports for a specific food (e.g. "medium"
     * -> 118g for a banana) - a separate call from {@link #search}/{@link #searchCandidates},
     * since the search response doesn't include usable portion data for Foundation/SR Legacy
     * foods; only the single-food detail endpoint does. Called once per newly FDC-cached {@code
     * FoodItem}, not per log, so the extra round trip is a one-time cost.
     *
     * @return every portion FDC reports, normalized, or empty if no key is configured, nothing
     *         came back, or the food has no portion data
     */
    public List<FdcPortion> fetchPortions(long fdcId) {
        if (apiKey == null || apiKey.isBlank()) {
            return List.of();
        }

        try {
            var response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https").host("api.nal.usda.gov").path("/fdc/v1/food/{fdcId}")
                            .queryParam("api_key", apiKey)
                            .build(fdcId))
                    .retrieve()
                    .body(FdcApiFoodDetail.class);

            if (response == null || response.foodPortions() == null) {
                return List.of();
            }

            return response.foodPortions().stream()
                    .filter(portion -> portion.gramWeight() != null && portion.modifier() != null
                            && !portion.modifier().isBlank())
                    .map(portion -> new FdcPortion(portion.modifier(), portion.gramWeight()))
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }
}
