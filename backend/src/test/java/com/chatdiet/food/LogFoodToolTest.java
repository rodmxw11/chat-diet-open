package com.chatdiet.food;

import com.chatdiet.fdc.FdcClient;
import com.chatdiet.fdc.FdcPortion;
import com.chatdiet.fdc.FdcProduct;
import com.chatdiet.fooditem.FoodItem;
import com.chatdiet.fooditem.FoodItemRepository;
import com.chatdiet.fooditem.PortionUnit;
import com.chatdiet.fooditem.PortionUnitRepository;
import com.chatdiet.intent.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises log_food's quantity+unit resolution tier (§6's PORTION_UNIT-backed path) - the whole
 * point being that ordinary, non-gram-weighed utterances ("2 eggs", "1 medium banana") can still
 * reach a deterministic lookup instead of hitting the model's estimator every time.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LogFoodToolTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void overrideDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("log-food-tool-test.db"));
    }

    @Autowired
    private LogFoodTool logFoodTool;

    @Autowired
    private FoodEntryRepository foodEntryRepository;

    @Autowired
    private FoodItemRepository foodItemRepository;

    @Autowired
    private PortionUnitRepository portionUnitRepository;

    @MockitoBean
    private FdcClient fdcClient;

    @BeforeEach
    void clearAll() {
        foodEntryRepository.deleteAll();
        portionUnitRepository.deleteAll();
        foodItemRepository.deleteAll();
    }

    @Test
    void quantityAndUnitResolveDeterministicallyAgainstAnAlreadyCachedPortion() {
        var bananaId = foodItemRepository.save(new FoodItem("banana", null,
                89.0, 1.1, 22.8, 0.3, 2.6, 12.2, 1.0, 0.1, 0.0, 358.0, null, "FDC")).id();
        portionUnitRepository.save(new PortionUnit(bananaId, "medium", 118.0, "FDC"));

        var request = new LogFoodRequest("banana", null, 1.0, "medium",
                105, 1.0, 27.0, 0.3, 3.0, 14.0, 1.0, 0.1, 0.0, 400.0, null);

        var result = logFoodTool.apply(request);

        assertThat(result).isInstanceOf(ToolResult.Success.class);
        var entry = (FoodEntry) ((ToolResult.Success) result).payload();
        // 89 cal/100g * 118g = 105.02 -> rounds to 105, matching the real cache, not the fallback estimate.
        assertThat(entry.totalCalories()).isEqualTo(105);
        assertThat(entry.amountGrams()).isEqualTo(118.0);
        verify(fdcClient, never()).search(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void quantityAndUnitResolveViaAFreshFdcMatchAndCacheItsPortionsForNextTime() {
        when(fdcClient.search("kiwi")).thenReturn(Optional.of(new FdcProduct(
                "Kiwifruit, raw", 61.0, 1.14, 14.66, 0.52, 3.0, 9.0, 3.0, 0.02, 0.0, 312.0, null, 99001L)));
        when(fdcClient.fetchPortions(99001L)).thenReturn(List.of(
                new FdcPortion("medium (2\" dia)", 76.0),
                new FdcPortion("large (2-1/4\" dia)", 91.0)));

        var request = new LogFoodRequest("kiwi", null, 1.0, "medium",
                60, 1.1, 15.0, 0.5, 3.0, 9.0, 3.0, 0.0, 0.0, 300.0, null);

        var result = logFoodTool.apply(request);

        assertThat(result).isInstanceOf(ToolResult.Success.class);
        var entry = (FoodEntry) ((ToolResult.Success) result).payload();
        // 61 cal/100g * 76g = 46.36 -> rounds to 46, not the model's 60-calorie fallback estimate.
        assertThat(entry.totalCalories()).isEqualTo(46);
        assertThat(entry.amountGrams()).isEqualTo(76.0);

        var cached = foodItemRepository.findById(entry.foodItemId()).orElseThrow();
        assertThat(cached.lookupSource()).isEqualTo("FDC");
        // Both fetched portions get stored, not just the one this request happened to need.
        assertThat(portionUnitRepository.findByFoodItemId(cached.id())).hasSize(2);
    }

    @Test
    void fallsThroughToTheModelEstimateWhenNoUnitResolvesAnywhere() {
        when(fdcClient.search("mystery fruit")).thenReturn(Optional.empty());

        var request = new LogFoodRequest("mystery fruit", null, 1.0, "handful",
                150, 2.0, 20.0, 5.0, 3.0, 10.0, 2.0, 0.5, 0.0, 200.0, null);

        var result = logFoodTool.apply(request);

        assertThat(result).isInstanceOf(ToolResult.Success.class);
        var entry = (FoodEntry) ((ToolResult.Success) result).payload();
        // The model's own fallback numbers, unscaled - no portion resolved anywhere.
        assertThat(entry.totalCalories()).isEqualTo(150);
        assertThat(entry.amountGrams()).isNull();
    }

    @Test
    void statingBothAGramAmountAndAUnitLearnsThatUnitsWeightForNextTime() {
        when(fdcClient.search("hard boiled egg")).thenReturn(Optional.empty());

        // "2 eggs, about 100g" - no cache/FDC match, so this falls to the model estimate, but
        // still teaches PORTION_UNIT that (for this food) "egg" means 50g.
        var firstRequest = new LogFoodRequest("hard boiled egg", 100.0, 2.0, "egg",
                155, 13.0, 1.1, 11.0, 0.0, 1.1, 62.0, 3.3, 373.0, 63.0, null);
        var firstResult = logFoodTool.apply(firstRequest);
        var firstEntry = (FoodEntry) ((ToolResult.Success) firstResult).payload();
        assertThat(firstEntry.foodItemId()).isNotNull();

        // A later mention with just the unit, no grams, should now resolve deterministically
        // against the just-learned portion instead of estimating again.
        var secondRequest = new LogFoodRequest("hard boiled egg", null, 1.0, "egg",
                999, 999.0, 999.0, 999.0, 999.0, 999.0, 999.0, 999.0, 999.0, 999.0, null);
        var secondResult = logFoodTool.apply(secondRequest);
        var secondEntry = (FoodEntry) ((ToolResult.Success) secondResult).payload();

        assertThat(secondEntry.amountGrams()).isEqualTo(50.0);
        // Scaled from the first entry's own per-100g-derived values, not the bogus 999 fallback.
        assertThat(secondEntry.totalCalories()).isNotEqualTo(999);
    }
}
