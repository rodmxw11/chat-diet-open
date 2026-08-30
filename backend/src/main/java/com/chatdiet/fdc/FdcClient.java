package com.chatdiet.fdc;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Looks up food nutrition data from USDA FoodData Central: Foundation Foods/SR Legacy
 * (analytically-sourced, unbranded foods) for named-food search, and Branded (manufacturer label
 * data) for UPC-verified lookups - see {@link #lookupBrandedByUpc}.
 *
 * <p>Requires a free api.data.gov key ({@code chat-diet.fdc.api-key}). If unset, every method
 * always returns empty so the food-lookup tier this feeds just falls through to the next one
 * rather than failing.
 *
 * <p>FDC's role here is recall, not automated selection - a numbered candidate list a person (or
 * the model relaying to one) picks from, never an auto-resolver. {@link #search} therefore returns
 * every plausible result FDC has, unfiltered; there is no plausibility rejection.
 */
@Service
public class FdcClient {

    private final RestClient restClient = RestClient.create();
    private final String apiKey;

    public FdcClient(@Value("${chat-diet.fdc.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * Searches Foundation Foods/SR Legacy for the given food name and returns up to {@code limit}
     * candidates - name and FDC id only, no nutrition, no filtering. Fetch a selected candidate's
     * nutrition via {@link #fetchDetail}, once it's actually picked rather than for every result
     * shown in a list.
     */
    public List<FdcCandidate> search(String query, int limit) {
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
                            .queryParam("requireAllWords", false)
                            .build())
                    .retrieve()
                    .body(FdcApiResponse.class);

            if (response == null || response.foods() == null) {
                return List.of();
            }

            return response.foods().stream()
                    .filter(food -> food.fdcId() != null && food.description() != null)
                    .sorted(rankingComparator(query))
                    .map(food -> new FdcCandidate(food.description(), food.fdcId()))
                    .limit(limit)
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Fetches full per-100g nutrition and known real-world portions for a specific food, in one
     * round trip - the search endpoint's response doesn't carry usable nutrient or portion data
     * for Foundation/SR Legacy foods, so this second call is made only once a candidate is
     * actually selected.
     *
     * @return empty if no key is configured, nothing came back, or the food has no usable calorie
     *         data
     */
    public Optional<FdcDetail> fetchDetail(long fdcId) {
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }

        try {
            var response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https").host("api.nal.usda.gov").path("/fdc/v1/food/{fdcId}")
                            .queryParam("api_key", apiKey)
                            .build(fdcId))
                    .retrieve()
                    .body(FdcApiFoodDetail.class);

            if (response == null) {
                return Optional.empty();
            }

            var product = FdcNutrientMapper.map(response);
            if (product == null) {
                return Optional.empty();
            }

            var portions = response.foodPortions() == null
                    ? List.<FdcPortion>of()
                    : response.foodPortions().stream()
                            .filter(portion -> portion.gramWeight() != null && portion.modifier() != null
                                    && !portion.modifier().isBlank())
                            .map(portion -> new FdcPortion(portion.modifier(), portion.gramWeight()))
                            .toList();

            return Optional.of(new FdcDetail(product, portions));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Looks up a packaged product by UPC against FDC's Branded data type - a second-tier fallback
     * behind Open Food Facts, since Branded has no barcode endpoint: a UPC query here is a
     * full-text search over hundreds of thousands of products, not an exact lookup. A hit is
     * accepted only if its own {@code gtinUpc} exactly matches the queried barcode (leading zeros
     * stripped from both sides) - no ranking heuristic, no fallback to FDC's own top result. An
     * unverified "match" here is worse than a miss.
     */
    public Optional<FdcDetail> lookupBrandedByUpc(String upc) {
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }

        var strippedQuery = stripLeadingZeros(upc);

        try {
            var response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https").host("api.nal.usda.gov").path("/fdc/v1/foods/search")
                            .queryParam("api_key", apiKey)
                            .queryParam("query", upc)
                            .queryParam("dataType", "Branded")
                            .queryParam("pageSize", 25)
                            .build())
                    .retrieve()
                    .body(FdcApiResponse.class);

            if (response == null || response.foods() == null) {
                return Optional.empty();
            }

            return response.foods().stream()
                    .filter(food -> food.fdcId() != null && food.gtinUpc() != null
                            && stripLeadingZeros(food.gtinUpc()).equals(strippedQuery))
                    .findFirst()
                    .flatMap(food -> fetchDetail(food.fdcId()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    static String stripLeadingZeros(String digits) {
        var stripped = digits.replaceFirst("^0+", "");
        return stripped.isEmpty() ? "0" : stripped;
    }

    /**
     * Ranks candidates so the most relevant come first: an exact match on the description's
     * leading token (before the first comma) beats a partial one, then more token overlap with the
     * query beats less, then a plainer (fewer-qualifier) description beats a more processed one.
     * Purely a display-ordering signal now, not a filter - nothing here excludes a result.
     */
    static Comparator<FdcApiFood> rankingComparator(String query) {
        var queryTokens = tokenize(query);
        return Comparator
                .comparing((FdcApiFood food) -> !leadingTokenMatches(query, food.description()))
                .thenComparing((FdcApiFood food) -> -tokenOverlap(queryTokens, food.description()))
                .thenComparingInt(food -> qualifierCount(food.description()));
    }

    private static boolean leadingTokenMatches(String query, String description) {
        if (description == null) return false;
        var leading = description.split(",", 2)[0].trim();
        return leading.equalsIgnoreCase(query.trim());
    }

    private static int tokenOverlap(Set<String> queryTokens, String description) {
        if (description == null) return 0;
        var descTokens = tokenize(description);
        return (int) queryTokens.stream().filter(descTokens::contains).count();
    }

    private static Set<String> tokenize(String text) {
        return Set.of(text.toLowerCase(Locale.ROOT).trim().split("[,\\s]+"));
    }

    /**
     * Rough measure of how qualified/processed a description is, by counting its comma- and
     * whitespace-separated tokens (e.g. "Bananas, raw" -> 2, "Bananas, dehydrated, or banana
     * powder" -> 5) - used as a ranking tiebreaker so the plainest match is shown first. Not real
     * NLP, just a cheap proxy: more description = more qualifiers.
     */
    static int qualifierCount(String description) {
        if (description == null || description.isBlank()) {
            return Integer.MAX_VALUE;
        }
        return description.split("[,\\s]+").length;
    }
}
