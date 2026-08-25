package com.chatdiet.food;

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
class FoodEntriesControllerTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void overrideDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("food-entries-controller-test.db"));
    }

    @Autowired
    private FoodEntriesController foodEntriesController;

    @Autowired
    private FoodEntryRepository foodEntryRepository;

    @BeforeEach
    void clearAll() {
        foodEntryRepository.deleteAll();
    }

    @Test
    void returnsEntriesWithinTheMetabolicDayChronologically() {
        var today = LocalDate.now();
        foodEntryRepository.save(new FoodEntry(today.atTime(14, 0), "lunch", 600, 30.0, 60.0, 20.0, "MANUAL"));
        foodEntryRepository.save(new FoodEntry(today.atTime(8, 0), "breakfast", 400, 20.0, 40.0, 10.0, "MANUAL"));

        var result = foodEntriesController.byDate(today);

        assertThat(result).extracting(FoodEntry::rawUtterance).containsExactly("breakfast", "lunch");
    }

    @Test
    void excludesEntriesJustAfterMidnightBeforeTheRolloverHour() {
        var today = LocalDate.now();
        // Rollover hour defaults to 4am, so 1am belongs to the *previous* metabolic day.
        foodEntryRepository.save(new FoodEntry(today.atTime(1, 0), "late night snack", 200, 5.0, 30.0, 5.0, "MANUAL"));
        foodEntryRepository.save(new FoodEntry(today.atTime(9, 0), "breakfast", 400, 20.0, 40.0, 10.0, "MANUAL"));

        var result = foodEntriesController.byDate(today);

        assertThat(result).extracting(FoodEntry::rawUtterance).containsExactly("breakfast");

        var previousDay = foodEntriesController.byDate(today.minusDays(1));
        assertThat(previousDay).extracting(FoodEntry::rawUtterance).containsExactly("late night snack");
    }

    @Test
    void returnsEmptyListWhenNothingLoggedThatDay() {
        var result = foodEntriesController.byDate(LocalDate.now());

        assertThat(result).isEmpty();
    }
}
