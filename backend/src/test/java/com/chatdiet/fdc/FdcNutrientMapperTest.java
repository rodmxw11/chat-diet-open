package com.chatdiet.fdc;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FdcNutrientMapperTest {

    private static FdcApiFoodNutrientDetail nutrient(int id, String unit, double amount) {
        return new FdcApiFoodNutrientDetail(new FdcApiFoodNutrientDetail.NutrientRef(id, "x", "x", unit), amount);
    }

    @Test
    void mapsKnownNutrientIdsToAppFields() {
        var detail = new FdcApiFoodDetail(173917L, "Celery, raw", List.of(
                nutrient(1008, "kcal", 16.0),
                nutrient(1003, "g", 0.69),
                nutrient(1005, "g", 2.97),
                nutrient(1004, "g", 0.17),
                nutrient(1079, "g", 1.6),
                nutrient(1093, "mg", 80.0),
                nutrient(1092, "mg", 260.0)
        ), null, 100.0, "g");

        var product = FdcNutrientMapper.map(detail);

        assertThat(product.name()).isEqualTo("Celery, raw");
        assertThat(product.caloriesPer100g()).isEqualTo(16.0);
        assertThat(product.proteinPer100g()).isEqualTo(0.69);
        assertThat(product.sodiumMgPer100g()).isEqualTo(80.0);
        assertThat(product.potassiumMgPer100g()).isEqualTo(260.0);
        assertThat(product.fdcId()).isEqualTo(173917L);
        // Not reported for this food - should stay null, not default to 0.
        assertThat(product.sugarPer100g()).isNull();
        assertThat(product.cholesterolMgPer100g()).isNull();
    }

    @Test
    void missingCaloriesMeansNoUsableProduct() {
        var detail = new FdcApiFoodDetail(1L, "Mystery item", List.of(nutrient(1003, "g", 5.0)), null, null, null);
        assertThat(FdcNutrientMapper.map(detail)).isNull();
    }

    @Test
    void unitMismatchThrowsRatherThanSilentlyMisreading() {
        var detail = new FdcApiFoodDetail(1L, "Weird food",
                List.of(nutrient(1008, "kj", 100.0)), null, null, null);
        assertThatThrownBy(() -> FdcNutrientMapper.map(detail)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void sugarTriesId2000ThenFallsBackTo1063() {
        var with2000 = new FdcApiFoodDetail(1L, "A", List.of(nutrient(1008, "kcal", 10.0), nutrient(2000, "g", 5.0),
                nutrient(1063, "g", 9.0)), null, null, null);
        assertThat(FdcNutrientMapper.map(with2000).sugarPer100g()).isEqualTo(5.0);

        var without2000 = new FdcApiFoodDetail(2L, "B", List.of(nutrient(1008, "kcal", 10.0), nutrient(1063, "g", 9.0)),
                null, null, null);
        assertThat(FdcNutrientMapper.map(without2000).sugarPer100g()).isEqualTo(9.0);

        var withNeither = new FdcApiFoodDetail(3L, "C", List.of(nutrient(1008, "kcal", 10.0)), null, null, null);
        assertThat(FdcNutrientMapper.map(withNeither).sugarPer100g()).isNull();
    }

    @Test
    void mlServingSizeIsRejectedButRestOfTheProductStillMaps() {
        var detail = new FdcApiFoodDetail(1L, "Orange juice", List.of(nutrient(1008, "kcal", 45.0)),
                null, 240.0, "ml");

        var product = FdcNutrientMapper.map(detail);

        assertThat(product.caloriesPer100g()).isEqualTo(45.0);
        assertThat(product.typicalServingG()).isNull();
    }

    @Test
    void gramServingSizeMapsThrough() {
        var detail = new FdcApiFoodDetail(1L, "Cereal", List.of(nutrient(1008, "kcal", 380.0)),
                null, 30.0, "g");

        assertThat(FdcNutrientMapper.map(detail).typicalServingG()).isEqualTo(30.0);
    }
}
