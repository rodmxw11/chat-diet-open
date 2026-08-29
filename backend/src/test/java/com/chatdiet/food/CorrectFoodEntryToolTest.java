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

    @Test
    void gramsCorrectionRecomputesFromTheCachedItemsPer100gValues() {
        var item = foodItemRepository.save(new FoodItem("chicken breast", null,
                165.0, 31.0, 0.0, 3.6, 0.0, 0.0, 74.0, 1.0, 85.0, 256.0, null, "FDC"));
        var entry = foodEntryRepository.save(new FoodEntry(LocalDateTime.now(), "chicken breast", 82,
                15.5, 0.0, 1.8, 0.0, 0.0, 37.0, 0.5, 42.5, 128.0, item.id(), 50.0, "FDC"));
        assertMostRecentIs(entry);

        var result = correctFoodEntryTool.apply(new CorrectFoodRequest(
                null, null, null, null, null, null, null, null, null, null, 100.0));

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
        var entry = foodEntryRepository.save(
                new FoodEntry(LocalDateTime.now(), "homemade chili", 450, 25.0, 40.0, 20.0, "MANUAL"));
        assertMostRecentIs(entry);

        var result = correctFoodEntryTool.apply(new CorrectFoodRequest(
                null, null, null, null, null, null, null, null, null, null, 300.0));

        assertThat(result).isInstanceOf(ToolResult.NeedsClarification.class);
        // Left unchanged - no recomputation was possible.
        assertThat(foodEntryRepository.findMostRecent().orElseThrow().totalCalories()).isEqualTo(450);
    }

    @Test
    void directValueCorrectionStillWorksWithoutAGramsAmount() {
        var entry = foodEntryRepository.save(
                new FoodEntry(LocalDateTime.now(), "pizza slice", 300, 12.0, 35.0, 10.0, "MANUAL"));
        assertMostRecentIs(entry);

        var result = correctFoodEntryTool.apply(new CorrectFoodRequest(
                350, null, null, null, null, null, null, null, null, null, null));

        assertThat(result).isInstanceOf(ToolResult.Success.class);
        var updated = (FoodEntry) ((ToolResult.Success) result).payload();
        assertThat(updated.totalCalories()).isEqualTo(350);
        assertThat(updated.totalProteinG()).isEqualTo(12.0);
        assertThat(updated.amountGrams()).isNull();
    }

    private void assertMostRecentIs(FoodEntry entry) {
        assertThat(foodEntryRepository.findMostRecent()).contains(entry);
    }
}
