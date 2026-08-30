package com.chatdiet.fdc;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FdcClientTest {

    private final FdcClient client = new FdcClient("unused-in-these-tests");

    @Test
    void plausibleMatchAcceptsSubstringInEitherDirection() {
        assertThat(FdcClient.isPlausibleMatch("celery", "Celery, raw")).isTrue();
        assertThat(FdcClient.isPlausibleMatch("Celery, raw", "celery")).isTrue();
        assertThat(FdcClient.isPlausibleMatch("yogurt", "Yogurt, plain, whole milk")).isTrue();
    }

    @Test
    void implausibleMatchIsRejected() {
        assertThat(FdcClient.isPlausibleMatch("celery", "Beef, ground, 80% lean")).isFalse();
    }

    @Test
    void nullDescriptionIsNeverAMatch() {
        assertThat(FdcClient.isPlausibleMatch("celery", null)).isFalse();
    }

    @Test
    void toProductMapsKnownNutrientIdsToAppFields() {
        var nutrients = List.of(
                new FdcApiNutrient(FdcNutrientMapping.CALORIES, 16.0),
                new FdcApiNutrient(FdcNutrientMapping.PROTEIN, 0.69),
                new FdcApiNutrient(FdcNutrientMapping.CARBS, 2.97),
                new FdcApiNutrient(FdcNutrientMapping.FAT, 0.17),
                new FdcApiNutrient(FdcNutrientMapping.FIBER, 1.6),
                new FdcApiNutrient(FdcNutrientMapping.SODIUM_MG, 80.0),
                new FdcApiNutrient(FdcNutrientMapping.POTASSIUM_MG, 260.0));
        var food = new FdcApiFood("Celery, raw", nutrients);

        var product = client.toProduct(food).orElseThrow();

        assertThat(product.name()).isEqualTo("Celery, raw");
        assertThat(product.caloriesPer100g()).isEqualTo(16.0);
        assertThat(product.proteinPer100g()).isEqualTo(0.69);
        assertThat(product.sodiumMgPer100g()).isEqualTo(80.0);
        assertThat(product.potassiumMgPer100g()).isEqualTo(260.0);
        // Not reported for this food - should stay null, not default to 0.
        assertThat(product.sugarPer100g()).isNull();
        assertThat(product.cholesterolMgPer100g()).isNull();
    }

    @Test
    void toProductIsEmptyWithoutUsableCalorieData() {
        var food = new FdcApiFood("Mystery item", List.of(new FdcApiNutrient(FdcNutrientMapping.PROTEIN, 5.0)));

        assertThat(client.toProduct(food)).isEmpty();
    }

    @Test
    void searchReturnsEmptyWhenNoApiKeyIsConfigured() {
        var unconfigured = new FdcClient("");

        assertThat(unconfigured.search("celery")).isEmpty();
    }

    @Test
    void searchCandidatesReturnsEmptyWhenNoApiKeyIsConfigured() {
        var unconfigured = new FdcClient("");

        assertThat(unconfigured.searchCandidates("celery", 5)).isEmpty();
    }

    @Test
    void qualifierCountPrefersPlainerDescriptions() {
        // Real case that motivated this: FDC's own relevance ranking put the powder ahead of the
        // raw fruit for a bare "banana" query - qualifier count should rank the plain one first.
        assertThat(FdcClient.qualifierCount("Bananas, raw"))
                .isLessThan(FdcClient.qualifierCount("Bananas, dehydrated, or banana powder"));
    }

    @Test
    void qualifierCountTreatsMissingDescriptionAsMaximallyQualified() {
        assertThat(FdcClient.qualifierCount(null)).isEqualTo(Integer.MAX_VALUE);
        assertThat(FdcClient.qualifierCount("")).isEqualTo(Integer.MAX_VALUE);
    }
}
