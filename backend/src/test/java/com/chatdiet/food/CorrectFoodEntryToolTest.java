package com.chatdiet.food;

import com.chatdiet.fooditem.FoodItem;
import com.chatdiet.fooditem.FoodItemRepository;
import com.chatdiet.intent.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CorrectFoodEntryToolTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void overrideDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("correct-food-entry-test.db"));
    }

    @Autowired
    private CorrectFoodEntryTool correctFoodEntryTool;

    @Autowired
    private FoodEntryRepository foodEntryRepository;

    @Autowired
    private FoodItemRepository foodItemRepository;

    @BeforeEach
    void clearAll() {
        foodEntryRepository.deleteAll();
        foodItemRepository.deleteAll();
    }

    /** Saves an entry the way the real logging path does: self-referential entryGroupId. */
    private FoodEntry saveAsOwnGroup(FoodEntry entry) {
        var saved = foodEntryRepository.save(entry);
        return foodEntryRepository.save(saved.withEntryGroupId(saved.id()));
    }

    @Test
    void gramsCorrectionRecomputesFromTheCachedItemsPer100gValues() {
        var item = foodItemRepository.save(new FoodItem("chicken breast", null,
                165.0, 31.0, 0.0, 3.6, 0.0, 0.0, 74.0, 1.0, 85.0, 256.0, null, "FDC"));
        saveAsOwnGroup(new FoodEntry(LocalDateTime.now(), "chicken breast", 82,
                15.5, 0.0, 1.8, 0.0, 0.0, 37.0, 0.5, 42.5, 128.0, item.id(), 50.0, "FDC"));

        var result = correctFoodEntryTool.apply(new CorrectFoodRequest(
                null, null, null, null, null, null, null, null, null, null, null, 100.0));

        assertThat(result).isInstanceOf(ToolResult.Success.class);
        var updated = (FoodEntry) ((ToolResult.Success) result).payload();
        assertThat(updated.amountGrams()).isEqualTo(100.0);
        assertThat(updated.totalCalories()).isEqualTo(165);
        assertThat(updated.totalProteinG()).isEqualTo(31.0);
        assertThat(updated.sodiumMg()).isEqualTo(74.0);
        assertThat(updated.correctedAt()).isNotNull();
        assertThat(updated.priorValuesJson()).contains("\"totalCalories\":82");
    }

    @Test
    void gramsCorrectionOnAnEntryWithNoCachedItemAsksForDirectValuesInstead() {
        saveAsOwnGroup(new FoodEntry(LocalDateTime.now(), "homemade chili", 450, 25.0, 40.0, 20.0, "MANUAL"));

        var result = correctFoodEntryTool.apply(new CorrectFoodRequest(
                null, null, null, null, null, null, null, null, null, null, null, 300.0));

        assertThat(result).isInstanceOf(ToolResult.NeedsClarification.class);
        // Left unchanged - no recomputation was possible.
        assertThat(foodEntryRepository.findMostRecent().orElseThrow().totalCalories()).isEqualTo(450);
    }

    @Test
    void directValueCorrectionStillWorksWithoutAGramsAmount() {
        saveAsOwnGroup(new FoodEntry(LocalDateTime.now(), "pizza slice", 300, 12.0, 35.0, 10.0, "MANUAL"));

        var result = correctFoodEntryTool.apply(new CorrectFoodRequest(
                null, 350, null, null, null, null, null, null, null, null, null, null));

        assertThat(result).isInstanceOf(ToolResult.Success.class);
        var updated = (FoodEntry) ((ToolResult.Success) result).payload();
        assertThat(updated.totalCalories()).isEqualTo(350);
        assertThat(updated.totalProteinG()).isEqualTo(12.0);
        assertThat(updated.amountGrams()).isNull();
    }

    @Test
    void singleEntryGroupCorrectsUnambiguouslyWithNoFoodRefNeeded() {
        saveAsOwnGroup(new FoodEntry(LocalDateTime.now(), "banana", 105, 1.3, 27.0, 0.4, "MANUAL"));

        var result = correctFoodEntryTool.apply(new CorrectFoodRequest(
                null, 110, null, null, null, null, null, null, null, null, null, null));

        assertThat(result).isInstanceOf(ToolResult.Success.class);
    }

    @Test
    void multiEntryGroupWithNoFoodRefAsksWhichOne() {
        var chicken = foodEntryRepository.save(new FoodEntry(LocalDateTime.now(), "chicken breast", 165,
                31.0, 0.0, 3.6, "FDC"));
        var bread = foodEntryRepository.save(new FoodEntry(LocalDateTime.now(), "wheat bread", 250,
                8.0, 45.0, 3.0, "MANUAL"));
        foodEntryRepository.save(chicken.withEntryGroupId(chicken.id()));
        foodEntryRepository.save(bread.withEntryGroupId(chicken.id()));

        var result = correctFoodEntryTool.apply(new CorrectFoodRequest(
                null, 300, null, null, null, null, null, null, null, null, null, null));

        assertThat(result).isInstanceOf(ToolResult.NeedsClarification.class);
    }

    @Test
    void multiEntryGroupResolvesByNamedFoodRef() {
        var chicken = foodEntryRepository.save(new FoodEntry(LocalDateTime.now(), "chicken breast", 165,
                31.0, 0.0, 3.6, "FDC"));
        var bread = foodEntryRepository.save(new FoodEntry(LocalDateTime.now(), "wheat bread", 250,
                8.0, 45.0, 3.0, "MANUAL"));
        foodEntryRepository.save(chicken.withEntryGroupId(chicken.id()));
        foodEntryRepository.save(bread.withEntryGroupId(chicken.id()));

        var result = correctFoodEntryTool.apply(new CorrectFoodRequest(
                "chicken", 200, null, null, null, null, null, null, null, null, null, null));

        assertThat(result).isInstanceOf(ToolResult.Success.class);
        var updated = (FoodEntry) ((ToolResult.Success) result).payload();
        assertThat(updated.rawUtterance()).isEqualTo("chicken breast");
        assertThat(updated.totalCalories()).isEqualTo(200);
    }
}
