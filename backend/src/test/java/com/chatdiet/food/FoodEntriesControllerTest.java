package com.chatdiet.food;

import com.chatdiet.chat.ChatMessageRepository;
import com.chatdiet.chat.ConversationHistoryStore;
import com.chatdiet.dashboard.DailyMacroCacheRepository;
import com.chatdiet.day.DayBoundaryService;
import com.chatdiet.fooditem.FoodItem;
import com.chatdiet.fooditem.FoodItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Autowired
    private DailyMacroCacheRepository dailyMacroCacheRepository;

    @Autowired
    private FoodItemRepository foodItemRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ConversationHistoryStore historyStore;

    @Autowired
    private DayBoundaryService dayBoundaryService;

    @BeforeEach
    void clearAll() {
        foodEntryRepository.deleteAll();
        chatMessageRepository.deleteAll();
        foodItemRepository.deleteAll();
        historyStore.clearAll();
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

    @Test
    void deletingAnEntryRemovesItAndRecomputesThatDaysMacroCache() {
        var today = LocalDate.now();
        var breakfast = foodEntryRepository.save(
                new FoodEntry(today.atTime(8, 0), "breakfast", 400, 20.0, 40.0, 10.0, "MANUAL"));
        foodEntryRepository.save(new FoodEntry(today.atTime(14, 0), "lunch", 600, 30.0, 60.0, 20.0, "MANUAL"));

        foodEntriesController.delete(breakfast.id());

        assertThat(foodEntriesController.byDate(today)).extracting(FoodEntry::rawUtterance).containsExactly("lunch");
        assertThat(dailyMacroCacheRepository.findByMetabolicDate(today))
                .get()
                .satisfies(cache -> assertThat(cache.totalCalories()).isEqualTo(600));
    }

    @Test
    void deletingAnUnknownIdReturns404() {
        assertThatThrownBy(() -> foodEntriesController.delete(999_999L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    private FoodItem savedJuice() {
        return foodItemRepository.save(new FoodItem("orange juice", null,
                45.0, 0.7, 10.4, 0.2, 0.2, 8.4, 1.0, 0.0, 0.0, 200.0, 240.0, "FDC"));
    }

    @Test
    void createLogsByItemIdAndPersistsTheChatExchange() {
        var juice = savedJuice();

        var response = foodEntriesController.create(
                new FoodEntryCreateRequest(juice.id(), 200.0, null, null, null));

        assertThat(response.reply()).startsWith("Logged \"orange juice\" (200g): 90 kcal");
        assertThat(response.entry().foodItemId()).isEqualTo(juice.id());
        assertThat(response.entry().amountGrams()).isEqualTo(200.0);

        var messages = historyStore.messagesFor(dayBoundaryService.today());
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).content()).isEqualTo("Scanned orange juice: 200g");
        assertThat(messages.get(1).content()).isEqualTo(response.reply());
    }

    @Test
    void createConvertsKcalAndServingsToGrams() {
        var juice = savedJuice();

        var byKcal = foodEntriesController.create(new FoodEntryCreateRequest(juice.id(), null, 90.0, null, null));
        assertThat(byKcal.entry().amountGrams()).isEqualTo(200.0);

        var byServings = foodEntriesController.create(new FoodEntryCreateRequest(juice.id(), null, null, 2.0, null));
        assertThat(byServings.entry().amountGrams()).isEqualTo(480.0);
    }

    @Test
    void createRejectsMissingOrConflictingQuantities() {
        var juice = savedJuice();

        assertThatThrownBy(() -> foodEntriesController.create(
                new FoodEntryCreateRequest(juice.id(), null, null, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
        assertThatThrownBy(() -> foodEntriesController.create(
                new FoodEntryCreateRequest(juice.id(), 100.0, 90.0, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void createRejectsUnknownOrDeletedItemsAndUnconvertibleKcal() {
        assertThatThrownBy(() -> foodEntriesController.create(
                new FoodEntryCreateRequest(999_999L, 100.0, null, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");

        var noCalories = foodItemRepository.save(new FoodItem("mystery tea", null,
                null, null, null, null, null, null, null, null, null, null, null, "MANUAL"));
        assertThatThrownBy(() -> foodEntriesController.create(
                new FoodEntryCreateRequest(noCalories.id(), null, 50.0, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }
}
