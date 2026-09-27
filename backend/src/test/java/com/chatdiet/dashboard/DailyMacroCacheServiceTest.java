package com.chatdiet.dashboard;

import com.chatdiet.food.FoodEntry;
import com.chatdiet.food.FoodEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DailyMacroCacheServiceTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void overrideDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("daily-macro-cache-test.db"));
    }

    @Autowired
    private DailyMacroCacheService dailyMacroCacheService;

    @Autowired
    private DailyMacroCacheRepository dailyMacroCacheRepository;

    @Autowired
    private FoodEntryRepository foodEntryRepository;

    @Autowired
    private MacroChartService macroChartService;

    @BeforeEach
    void clearAll() {
        foodEntryRepository.deleteAll();
        dailyMacroCacheRepository.deleteAll();
    }

    @Test
    void recomputeSumsAllEntriesOnTheDay() {
        var today = LocalDate.now();
        foodEntryRepository.save(new FoodEntry(today.atTime(9, 0), "breakfast", 400, 20.0, 40.0, 10.0, "LOG_FOOD"));
        foodEntryRepository.save(new FoodEntry(today.atTime(13, 0), "lunch", 600, 30.0, 60.0, 20.0, "LOG_FOOD"));

        dailyMacroCacheService.recompute(today);

        var cached = dailyMacroCacheRepository.findByMetabolicDate(today).orElseThrow();
        assertThat(cached.totalCalories()).isEqualTo(1000);
        assertThat(cached.proteinG()).isEqualTo(50.0);
        assertThat(cached.carbsG()).isEqualTo(100.0);
        assertThat(cached.fatG()).isEqualTo(30.0);
    }

    @Test
    void recomputeUpsertsRatherThanDuplicating() {
        var today = LocalDate.now();
        foodEntryRepository.save(new FoodEntry(today.atTime(9, 0), "breakfast", 400, 20.0, 40.0, 10.0, "LOG_FOOD"));
        dailyMacroCacheService.recompute(today);

        foodEntryRepository.save(new FoodEntry(today.atTime(13, 0), "lunch", 600, 30.0, 60.0, 20.0, "LOG_FOOD"));
        dailyMacroCacheService.recompute(today);

        assertThat(dailyMacroCacheRepository.findByMetabolicDateBetween(today, today)).hasSize(1);
        assertThat(dailyMacroCacheRepository.findByMetabolicDate(today).orElseThrow().totalCalories()).isEqualTo(1000);
    }

    @Test
    void macroChartServiceReadsFromCacheAndFillsGapsWithZero() {
        var today = LocalDate.now();
        foodEntryRepository.save(new FoodEntry(today.atTime(9, 0), "breakfast", 400, 20.0, 40.0, 10.0, "LOG_FOOD"));
        dailyMacroCacheService.recompute(today);

        var result = macroChartService.dailyMacros(today.minusDays(1), today);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).calories()).isZero();
        assertThat(result.get(1).calories()).isEqualTo(400);
    }

    @Test
    void backfillPopulatesCacheForPreExistingEntriesOnStartup() {
        var today = LocalDate.now();
        foodEntryRepository.save(new FoodEntry(today.atTime(9, 0), "breakfast", 400, 20.0, 40.0, 10.0, "LOG_FOOD"));
        dailyMacroCacheRepository.deleteAll();

        dailyMacroCacheService.backfill();

        var cached = dailyMacroCacheRepository.findByMetabolicDate(today);
        assertThat(cached).isPresent();
        assertThat(cached.get().totalCalories()).isEqualTo(400);
    }

    /** A day whose only entry was deleted is no longer logged - not a 0-calorie "fast" for TDEE. */
    @Test
    void deletingADaysLastEntryRemovesItsRow() {
        var today = LocalDate.now();
        var entry = foodEntryRepository.save(
                new FoodEntry(today.atTime(9, 0), "banana", 89, 1.1, 22.8, 0.3, "LOG_FOOD"));
        dailyMacroCacheService.recompute(today);
        assertThat(dailyMacroCacheRepository.findByMetabolicDate(today)).isPresent();

        foodEntryRepository.delete(entry);
        dailyMacroCacheService.recompute(today);

        assertThat(dailyMacroCacheRepository.findByMetabolicDate(today)).isEmpty();
    }

    /** A deliberate fast is logged as a 0-calorie entry, and that day does count - as a real 0. */
    @Test
    void aZeroCalorieFastEntryKeepsAZeroRow() {
        var today = LocalDate.now();
        foodEntryRepository.save(new FoodEntry(today.atTime(9, 0), "fasted", 0, 0.0, 0.0, 0.0, "LOG_FOOD"));

        dailyMacroCacheService.recompute(today);

        assertThat(dailyMacroCacheRepository.findByMetabolicDate(today).orElseThrow().totalCalories()).isZero();
    }

    /** A forgotten day in the middle of the history must not become a 0-calorie row on restart. */
    @Test
    void backfillSkipsDaysWithNoEntriesAndClearsStaleZeroRows() {
        var today = LocalDate.now();
        var gapDay = today.minusDays(1);
        foodEntryRepository.save(new FoodEntry(today.minusDays(2).atTime(9, 0), "lunch", 500, 0.0, 0.0, 0.0, "LOG_FOOD"));
        foodEntryRepository.save(new FoodEntry(today.atTime(9, 0), "breakfast", 400, 0.0, 0.0, 0.0, "LOG_FOOD"));
        dailyMacroCacheRepository.save(new DailyMacroCache(null, gapDay, 0, 0, 0, 0, java.time.LocalDateTime.now()));

        dailyMacroCacheService.backfill();

        assertThat(dailyMacroCacheRepository.findByMetabolicDate(gapDay)).isEmpty();
        assertThat(dailyMacroCacheRepository.findByMetabolicDate(today.minusDays(2))).isPresent();
        assertThat(dailyMacroCacheRepository.findByMetabolicDate(today)).isPresent();
    }
}
