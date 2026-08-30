package com.chatdiet.food;

import com.chatdiet.fdc.FdcClient;
import com.chatdiet.fdc.FdcDetail;
import com.chatdiet.fdc.FdcPortion;
import com.chatdiet.fdc.FdcProduct;
import com.chatdiet.fooditem.FoodAlias;
import com.chatdiet.fooditem.FoodAliasRepository;
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
import static org.mockito.Mockito.when;

/**
 * Exercises log_food's identity/quantity resolution split: an alias hit resolves silently, a miss
 * produces a numbered clarification (never an auto-guess), and a selection on a follow-up call
 * writes an alias only when the original phrase was genuinely new, not ambiguous.
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
    private FoodAliasRepository foodAliasRepository;

    @Autowired
    private PortionUnitRepository portionUnitRepository;

    @MockitoBean
    private FdcClient fdcClient;

    @BeforeEach
    void clearAll() {
        foodEntryRepository.deleteAll();
        portionUnitRepository.deleteAll();
        foodAliasRepository.deleteAll();
        foodItemRepository.deleteAll();
    }

    private static LogFoodItemRequest item(String foodRef, String amountText, int calories) {
        return new LogFoodItemRequest(foodRef, amountText, null, null, null,
                calories, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0);
    }

    @Test
    void aliasHitResolvesDeterministicallyWithoutTouchingFdc() {
        var bananaId = foodItemRepository.save(new FoodItem("banana", null,
                89.0, 1.1, 22.8, 0.3, 2.6, 12.2, 1.0, 0.1, 0.0, 358.0, null, "FDC")).id();
        foodAliasRepository.save(new FoodAlias("banana", bananaId, "USER"));
        portionUnitRepository.save(new PortionUnit(bananaId, "medium", 118.0, "FDC"));

        var request = new LogFoodRequest(List.of(item("banana", "1 medium", 105)), null, null);

        var result = logFoodTool.apply(request);

        assertThat(result).isInstanceOf(ToolResult.Success.class);
        var entry = (FoodEntry) ((ToolResult.Success) result).payload();
        // 89 cal/100g * 118g = 105.02 -> rounds to 105, matching the real cache, not the fallback estimate.
        assertThat(entry.totalCalories()).isEqualTo(105);
        assertThat(entry.amountGrams()).isEqualTo(118.0);
        assertThat(entry.entryGroupId()).isEqualTo(entry.id());
    }

    @Test
    void unresolvedPhraseReturnsANumberedClarificationInsteadOfGuessing() {
        var honeyId = foodItemRepository.save(new FoodItem("Honey Wheat Bread", null,
                250.0, 8.0, 45.0, 3.0, 2.0, 5.0, 300.0, 0.5, 0.0, 100.0, null, "MANUAL")).id();
        var wholeId = foodItemRepository.save(new FoodItem("Whole Wheat Bread", null,
                240.0, 9.0, 44.0, 3.5, 3.0, 4.0, 280.0, 0.6, 0.0, 100.0, null, "MANUAL")).id();

        var request = new LogFoodRequest(List.of(item("wheat bread", "2 slices", 200)), null, null);

        var result = logFoodTool.apply(request);

        assertThat(result).isInstanceOf(ToolResult.NeedsClarification.class);
        var question = ((ToolResult.NeedsClarification) result).question();
        assertThat(question).contains("[ref:item:" + honeyId + "]", "[ref:item:" + wholeId + "]");
        assertThat(foodEntryRepository.findAll()).isEmpty();
    }

    @Test
    void pickingACandidateFromAnAmbiguousListDoesNotWriteAnAlias() {
        var honey = foodItemRepository.save(new FoodItem("Honey Wheat Bread", null,
                250.0, 8.0, 45.0, 3.0, 2.0, 5.0, 300.0, 0.5, 0.0, 100.0, null, "MANUAL"));
        foodItemRepository.save(new FoodItem("Whole Wheat Bread", null,
                240.0, 9.0, 44.0, 3.5, 3.0, 4.0, 280.0, 0.6, 0.0, 100.0, null, "MANUAL"));

        var selection = new LogFoodItemRequest("wheat bread", "100g", honey.id(), null, null,
                null, null, null, null, null, null, null, null, null, null);
        var result = logFoodTool.apply(new LogFoodRequest(List.of(selection), null, null));

        assertThat(result).isInstanceOf(ToolResult.Success.class);
        assertThat(foodAliasRepository.findByAliasNormalized("wheat bread")).isEmpty();
    }

    @Test
    void pickingACandidateFromAnUnknownListWritesAnAliasForNextTime() {
        var celery = foodItemRepository.save(new FoodItem("celery", null,
                16.0, 0.7, 3.0, 0.2, 1.6, 1.3, 80.0, 0.0, 0.0, 260.0, null, "FDC"));
        when(fdcClient.search("celery sticks", 5)).thenReturn(List.of());

        var selection = new LogFoodItemRequest("celery sticks", "24g", celery.id(), null, null,
                null, null, null, null, null, null, null, null, null, null);
        logFoodTool.apply(new LogFoodRequest(List.of(selection), null, null));

        assertThat(foodAliasRepository.findByAliasNormalized("celery sticks")).isPresent();

        // The next mention of the same phrase now resolves silently.
        var second = logFoodTool.apply(new LogFoodRequest(List.of(item("celery sticks", "24g", 999)), null, null));
        assertThat(second).isInstanceOf(ToolResult.Success.class);
    }

    @Test
    void pickingAnFdcCandidateCachesItAndStoresItsPortions() {
        when(fdcClient.fetchDetail(99001L)).thenReturn(Optional.of(new FdcDetail(
                new FdcProduct("Kiwifruit, raw", 61.0, 1.14, 14.66, 0.52, 3.0, 9.0, 3.0, 0.02, 0.0, 312.0, null, 99001L),
                List.of(new FdcPortion("medium (2\" dia)", 76.0), new FdcPortion("large (2-1/4\" dia)", 91.0)))));

        var selection = new LogFoodItemRequest("kiwi", "1 medium", null, 99001L, null,
                null, null, null, null, null, null, null, null, null, null);
        var result = logFoodTool.apply(new LogFoodRequest(List.of(selection), null, null));

        assertThat(result).isInstanceOf(ToolResult.Success.class);
        var entry = (FoodEntry) ((ToolResult.Success) result).payload();
        var cached = foodItemRepository.findById(entry.foodItemId()).orElseThrow();
        assertThat(cached.lookupSource()).isEqualTo("FDC");
        assertThat(portionUnitRepository.findByFoodItemId(cached.id())).hasSize(2);
        // 61 cal/100g * 76g = 46.36 -> rounds to 46.
        assertThat(entry.totalCalories()).isEqualTo(46);
    }

    @Test
    void useEstimateLogsTheModelsOwnNumbersAndCachesAReusableItemWhenGramsAreStated() {
        var itemReq = new LogFoodItemRequest("homemade chili", "300g", null, null, true,
                450, 25.0, 40.0, 20.0, 8.0, 5.0, 600.0, 6.0, 40.0, 700.0);

        var result = logFoodTool.apply(new LogFoodRequest(List.of(itemReq), null, null));

        assertThat(result).isInstanceOf(ToolResult.Success.class);
        var entry = (FoodEntry) ((ToolResult.Success) result).payload();
        assertThat(entry.totalCalories()).isEqualTo(450);
        assertThat(entry.foodItemId()).isNotNull();
        assertThat(foodAliasRepository.findByAliasNormalized("homemade chili")).isPresent();
    }

    @Test
    void previouslySub10CalorieItemsNowLogNormally() {
        var itemReq = new LogFoodItemRequest("black tea", "", null, null, true,
                2, 0.0, 0.5, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);

        var result = logFoodTool.apply(new LogFoodRequest(List.of(itemReq), null, null));

        assertThat(result).isInstanceOf(ToolResult.Success.class);
        assertThat(foodEntryRepository.findAll()).hasSize(1);
    }

    @Test
    void partialBatchSavesResolvedItemsAndClarifiesTheRestIntoTheSameGroup() {
        var chicken = foodItemRepository.save(new FoodItem("chicken breast", null,
                165.0, 31.0, 0.0, 3.6, 0.0, 0.0, 74.0, 1.0, 85.0, 256.0, null, "FDC"));
        foodAliasRepository.save(new FoodAlias("chicken breast", chicken.id(), "USER"));
        foodItemRepository.save(new FoodItem("Honey Wheat Bread", null,
                250.0, 8.0, 45.0, 3.0, 2.0, 5.0, 300.0, 0.5, 0.0, 100.0, null, "MANUAL"));
        foodItemRepository.save(new FoodItem("Whole Wheat Bread", null,
                240.0, 9.0, 44.0, 3.5, 3.0, 4.0, 280.0, 0.6, 0.0, 100.0, null, "MANUAL"));

        var request = new LogFoodRequest(
                List.of(item("chicken breast", "150g", 200), item("wheat bread", "2 slices", 200)), null, null);

        var result = logFoodTool.apply(request);

        assertThat(result).isInstanceOf(ToolResult.NeedsClarification.class);
        assertThat(foodEntryRepository.findAll()).hasSize(1);
        var savedEntry = foodEntryRepository.findAll().get(0);
        assertThat(savedEntry.entryGroupId()).isEqualTo(savedEntry.id());

        var question = ((ToolResult.NeedsClarification) result).question();
        assertThat(question).contains("group #" + savedEntry.entryGroupId());

        // The clarifying follow-up attaches to the same group.
        var followUp = new LogFoodItemRequest("wheat bread", "100g",
                foodItemRepository.search("Honey", false).get(0).id(), null, null,
                null, null, null, null, null, null, null, null, null, null);
        var followUpRequest = new LogFoodRequest(List.of(followUp), savedEntry.entryGroupId(), null);
        var followUpResult = logFoodTool.apply(followUpRequest);

        assertThat(followUpResult).isInstanceOf(ToolResult.Success.class);
        assertThat(foodEntryRepository.findAll()).hasSize(2);
        assertThat(foodEntryRepository.findAll()).allMatch(e -> e.entryGroupId().equals(savedEntry.entryGroupId()));
    }

    @Test
    void statingBothAGramAmountAndAUnitLearnsThatUnitsWeightForNextTime() {
        // "2 eggs, about 100g" - no cache/FDC match, so this falls to the model estimate (chosen via
        // useEstimate), but still teaches PORTION_UNIT that (for this food) "egg" means 50g.
        var firstReq = new LogFoodItemRequest("hard boiled egg", "2 eggs, about 100g", null, null, true,
                155, 13.0, 1.1, 11.0, 0.0, 1.1, 62.0, 3.3, 373.0, 63.0);
        var firstResult = logFoodTool.apply(new LogFoodRequest(List.of(firstReq), null, null));
        var firstEntry = (FoodEntry) ((ToolResult.Success) firstResult).payload();
        assertThat(firstEntry.foodItemId()).isNotNull();

        // A later mention with just the unit, no grams, resolves against the just-learned portion -
        // the estimate selection above already wrote an alias for "hard boiled egg".
        assertThat(foodAliasRepository.findByAliasNormalized("hard boiled egg")).isPresent();

        var secondReq = item("hard boiled egg", "1 egg", 999);
        var secondResult = logFoodTool.apply(new LogFoodRequest(List.of(secondReq), null, null));
        var secondEntry = (FoodEntry) ((ToolResult.Success) secondResult).payload();

        assertThat(secondEntry.amountGrams()).isEqualTo(50.0);
        // Scaled from the first entry's own per-100g-derived values, not the bogus 999 fallback.
        assertThat(secondEntry.totalCalories()).isNotEqualTo(999);
    }
}
