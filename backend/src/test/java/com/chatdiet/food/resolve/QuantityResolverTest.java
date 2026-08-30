package com.chatdiet.food.resolve;

import com.chatdiet.fooditem.FoodItem;
import com.chatdiet.fooditem.PortionUnit;
import com.chatdiet.fooditem.PortionUnitRepository;
import com.chatdiet.fooditem.PortionUnitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class QuantityResolverTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void overrideDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("quantity-resolver-test.db"));
    }

    @Autowired
    private QuantityResolver quantityResolver;

    @Autowired
    private PortionUnitRepository portionUnitRepository;

    @Autowired
    private PortionUnitService portionUnitService;

    private FoodItem banana;

    @BeforeEach
    void setUp() {
        portionUnitRepository.deleteAll();
        banana = new FoodItem(1L, "banana", null, 89.0, 1.1, 22.8, 0.3, 2.6, 12.2, 1.0, 0.1, 0.0, 358.0,
                null, "FDC", 0, null, null);
        portionUnitService.storePortions(1L, java.util.List.of(new com.chatdiet.fdc.FdcPortion("medium", 118.0)));
    }

    @Test
    void parsesExplicitGrams() {
        assertThat(quantityResolver.resolve(banana, "142g")).isEqualTo(new QuantityResolution.Grams(142.0));
        assertThat(quantityResolver.resolve(banana, "142 grams")).isEqualTo(new QuantityResolution.Grams(142.0));
    }

    @Test
    void parsesCountAndKnownUnit() {
        assertThat(quantityResolver.resolve(banana, "2 medium")).isEqualTo(new QuantityResolution.Grams(236.0));
    }

    @Test
    void parsesArticlePlusUnitAsOneCount() {
        var item = new FoodItem(2L, "oatmeal", null, 68.0, 2.4, 12.0, 1.4, 1.7, 0.5, 4.0, 0.2, 0.0, 61.0,
                null, "MANUAL", 0, null, null);
        portionUnitService.storePortions(2L, java.util.List.of(new com.chatdiet.fdc.FdcPortion("bowl", 250.0)));
        assertThat(quantityResolver.resolve(item, "a bowl")).isEqualTo(new QuantityResolution.Grams(250.0));
    }

    @Test
    void bareNumberScalesByTypicalServing() {
        var item = new FoodItem(3L, "clif bar", null, 380.0, 10.0, 65.0, 7.0, 5.0, 21.0, 210.0, 2.0, 0.0, 200.0,
                68.0, "MANUAL", 0, null, null);
        assertThat(quantityResolver.resolve(item, "2")).isEqualTo(new QuantityResolution.Grams(136.0));
    }

    @Test
    void emptyOrUnknownUnitIsUnresolvable() {
        assertThat(quantityResolver.resolve(banana, "")).isInstanceOf(QuantityResolution.Unresolvable.class);
        assertThat(quantityResolver.resolve(banana, "a handful")).isInstanceOf(QuantityResolution.Unresolvable.class);
    }

    @Test
    void explicitGramsOrServingsPairResolvesForUpcFlow() {
        var item = new FoodItem(4L, "clif bar", null, 380.0, 10.0, 65.0, 7.0, 5.0, 21.0, 210.0, 2.0, 0.0, 200.0,
                68.0, "UPC", 0, null, null);
        assertThat(quantityResolver.resolve(item, 150.0, null)).isEqualTo(new QuantityResolution.Grams(150.0));
        assertThat(quantityResolver.resolve(item, null, 2.0)).isEqualTo(new QuantityResolution.Grams(136.0));
    }
}
