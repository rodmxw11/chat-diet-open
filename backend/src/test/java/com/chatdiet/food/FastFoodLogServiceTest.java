package com.chatdiet.food;

import com.chatdiet.chat.ChatMessageRepository;
import com.chatdiet.chat.ConversationHistoryStore;
import com.chatdiet.fooditem.FoodAlias;
import com.chatdiet.fooditem.FoodAliasRepository;
import com.chatdiet.fooditem.FoodItem;
import com.chatdiet.fooditem.FoodItemRepository;
import com.chatdiet.fooditem.PortionUnit;
import com.chatdiet.fooditem.PortionUnitRepository;
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
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the deterministic fast path end to end against a real temp database: a recognized
 * quantity + exact alias logs a FOOD_ENTRY and persists both chat halves; everything else - the
 * historical shapes that need the model - returns empty and writes nothing.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FastFoodLogServiceTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void overrideDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("fast-food-log-test.db"));
    }

    @Autowired
    private FastFoodLogService fastFoodLogService;

    @Autowired
    private FoodItemRepository foodItemRepository;

    @Autowired
    private FoodAliasRepository foodAliasRepository;

    @Autowired
    private PortionUnitRepository portionUnitRepository;

    @Autowired
    private FoodEntryRepository foodEntryRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ConversationHistoryStore historyStore;

    private final LocalDate today = LocalDate.now();
    private final LocalDateTime now = LocalDateTime.now();

    private FoodItem juice;

    @BeforeEach
    void seed() {
        foodEntryRepository.deleteAll();
        chatMessageRepository.deleteAll();
        portionUnitRepository.deleteAll();
        foodAliasRepository.deleteAll();
        foodItemRepository.deleteAll();
        historyStore.clearAll();

        juice = foodItemRepository.save(new FoodItem("orange juice", null,
                45.0, 0.7, 10.4, 0.2, 0.2, 8.4, 1.0, 0.0, 0.0, 200.0, 240.0, "FDC"));
        foodAliasRepository.save(new FoodAlias("orange juice", juice.id(), "MANUAL"));
    }

    @Test
    void gramsFormLogsAndPersistsBothChatHalves() {
        var reply = fastFoodLogService.tryHandle(today, now, "I ate 200 g of orange juice");

        assertThat(reply).isPresent();
        assertThat(reply.get()).startsWith("Logged \"orange juice\" (200g): 90 kcal");

        var entries = foodEntryRepository.findAll();
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).totalCalories()).isEqualTo(90);
        assertThat(entries.get(0).amountGrams()).isEqualTo(200.0);

        var messages = historyStore.messagesFor(today);
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).content()).isEqualTo("I ate 200 g of orange juice");
        assertThat(messages.get(1).content()).isEqualTo(reply.get());
    }

    @Test
    void calorieFormInvertsToGrams() {
        var reply = fastFoodLogService.tryHandle(today, now, "90 cal orange juice");

        assertThat(reply).isPresent();
        var entry = foodEntryRepository.findAll().get(0);
        assertThat(entry.amountGrams()).isEqualTo(200.0);
    }

    @Test
    void countFormUsesTypicalServingAndUnitSplitUsesPortionUnits() {
        var servingsReply = fastFoodLogService.tryHandle(today, now, "2 orange juice");
        assertThat(servingsReply).isPresent();
        assertThat(foodEntryRepository.findAll().get(0).amountGrams()).isEqualTo(480.0);

        portionUnitRepository.save(new PortionUnit(juice.id(), "glass", 250.0, "MANUAL"));
        var unitReply = fastFoodLogService.tryHandle(today, now, "I ate 2 glasses orange juice");
        assertThat(unitReply).isPresent();
        assertThat(foodEntryRepository.findAll()).hasSize(2);
        assertThat(foodEntryRepository.findAll().get(1).amountGrams()).isEqualTo(500.0);
    }

    @Test
    void unknownAliasesCorrectionsAndMultiItemFallThroughUntouched() {
        assertThat(fastFoodLogService.tryHandle(today, now, "142g cheerios")).isEmpty();
        assertThat(fastFoodLogService.tryHandle(today, now, "yesterday i ate 100 g of orange juice")).isEmpty();
        assertThat(fastFoodLogService.tryHandle(today, now, "make that 50g")).isEmpty();
        assertThat(fastFoodLogService.tryHandle(today, now, "I ate 100g orange juice and toast")).isEmpty();
        assertThat(fastFoodLogService.tryHandle(today, now, "180 lbs")).isEmpty();

        assertThat(foodEntryRepository.findAll()).isEmpty();
        assertThat(historyStore.messagesFor(today)).isEmpty();
    }

    @Test
    void aSoftDeletedAliasTargetFallsThrough() {
        foodItemRepository.save(juice.withDeleted());

        assertThat(fastFoodLogService.tryHandle(today, now, "200g orange juice")).isEmpty();
        assertThat(foodEntryRepository.findAll()).isEmpty();
    }
}