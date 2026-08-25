package com.chatdiet.intent;

import com.chatdiet.chat.ChatMessageRepository;
import com.chatdiet.chat.ChatService;
import com.chatdiet.chat.ConversationHistoryStore;
import com.chatdiet.digestive.DigestiveEventRepository;
import com.chatdiet.exercise.ExerciseEntryRepository;
import com.chatdiet.food.FoodEntryRepository;
import com.chatdiet.fooditem.FoodItemRepository;
import com.chatdiet.note.NoteRepository;
import com.chatdiet.nutrition.DailyTargetRepository;
import com.chatdiet.shopping.ShoppingItemRepository;
import com.chatdiet.sql.SavedQueryRepository;
import com.chatdiet.vitals.VitalsEntryRepository;
import com.chatdiet.weight.WeightEntryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class IntentRoutingTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void overrideDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("routing-test.db"));
    }

    @Autowired
    private ChatService chatService;

    @Autowired
    private NoteRepository noteRepository;

    @Autowired
    private FoodEntryRepository foodEntryRepository;

    @Autowired
    private WeightEntryRepository weightEntryRepository;

    @Autowired
    private VitalsEntryRepository vitalsEntryRepository;

    @Autowired
    private ExerciseEntryRepository exerciseEntryRepository;

    @Autowired
    private DigestiveEventRepository digestiveEventRepository;

    @Autowired
    private DailyTargetRepository dailyTargetRepository;

    @Autowired
    private FoodItemRepository foodItemRepository;

    @Autowired
    private ConversationHistoryStore conversationHistoryStore;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ShoppingItemRepository shoppingItemRepository;

    @Autowired
    private SavedQueryRepository savedQueryRepository;

    @BeforeEach
    void clearAll() {
        // Tools like show_chart depend on request-scoped beans; bind a mock request so they
        // resolve here too, not just when invoked through the real ChatController.
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));

        noteRepository.deleteAll();
        foodEntryRepository.deleteAll();
        weightEntryRepository.deleteAll();
        vitalsEntryRepository.deleteAll();
        exerciseEntryRepository.deleteAll();
        digestiveEventRepository.deleteAll();
        dailyTargetRepository.deleteAll();
        foodItemRepository.deleteAll();
        // Both are needed: clearAll() only drops the in-memory cache, and the store reloads a
        // day's context from chat_message on next access - so without this the previous test's
        // turns would be replayed into this one.
        conversationHistoryStore.clearAll();
        chatMessageRepository.deleteAll();
        shoppingItemRepository.deleteAll();
        savedQueryRepository.deleteAll();
    }

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void routesNoteTakingUtteranceToSaveNoteTool() {
        chatService.reply("remind myself to buy more almond milk");

        assertThat(noteRepository.findAll())
                .as("save_note should have been invoked and persisted a Note")
                .hasSize(1);
    }

    @Test
    void routesAlternatePhrasingToSaveNoteTool() {
        chatService.reply("note to self: check the mail");

        assertThat(noteRepository.findAll())
                .as("save_note should have been invoked and persisted a Note")
                .hasSize(1);
    }

    @Test
    void routesNamedFoodUtteranceToLogFoodTool() {
        chatService.reply("I ate a medium banana");

        assertThat(foodEntryRepository.findAll())
                .as("log_food should have been invoked and persisted a FoodEntry")
                .hasSize(1);
    }

    @Test
    void routesWeightUtteranceToLogWeightTool() {
        chatService.reply("weight this morning was 182.4 lbs");

        assertThat(weightEntryRepository.findAll())
                .as("log_weight should have been invoked and persisted a WeightEntry")
                .hasSize(1);
    }

    @Test
    void routesVitalsUtteranceToLogVitalsTool() {
        chatService.reply("my blood pressure is 128 over 82, heart rate 68");

        assertThat(vitalsEntryRepository.findAll())
                .as("log_vitals should have been invoked and persisted a VitalsEntry")
                .hasSize(1);
    }

    @Test
    void routesExerciseUtteranceToLogExerciseTool() {
        chatService.reply("I ran for 30 minutes");

        assertThat(exerciseEntryRepository.findAll())
                .as("log_exercise should have been invoked and persisted an ExerciseEntry")
                .hasSize(1);
    }

    @Test
    void routesDigestiveEventUtteranceToLogDigestiveEventTool() {
        chatService.reply("had some mild reflux after lunch");

        assertThat(digestiveEventRepository.findAll())
                .as("log_digestive_event should have been invoked and persisted a DigestiveEvent")
                .hasSize(1);
    }

    @Test
    void routesFeatureRequestToSaveNoteTool() {
        chatService.reply("it would be great if the app could remind me to log weight every morning");

        assertThat(noteRepository.findAll())
                .as("save_note should have been invoked and persisted a Note for an app feature request")
                .hasSize(1);
    }

    @Test
    void routesMarkedCorrectionToCorrectWeightEntryTool() {
        chatService.reply("weight this morning was 182.4 lbs");
        assertThat(weightEntryRepository.findAll()).hasSize(1);

        chatService.reply("the scale actually said 181.0");

        var entries = weightEntryRepository.findAll();
        assertThat(entries)
                .as("correct_weight_entry should update the existing row in place, not add a new one")
                .hasSize(1);
        assertThat(entries.iterator().next().correctedAt())
                .as("corrected entry should have correctedAt set")
                .isNotNull();
    }

    @Test
    void routesSetGoalUtteranceToSetCalorieGoalTool() {
        chatService.reply("set my calorie goal to 2000 a day");

        assertThat(dailyTargetRepository.findAll())
                .as("set_calorie_goal should have been invoked and persisted a DailyTarget")
                .hasSize(1);
    }

    @Test
    void routesCalorieTargetQuestionToGetDailyTargetTool() {
        chatService.reply("set my calorie goal to 2000 a day");
        chatService.reply("I ate a chicken sandwich, 450 calories, 30g protein, 40g carbs, 15g fat");

        var reply = chatService.reply("what's my calorie target for today and how many calories do I have left?");

        assertThat(reply).as("get_daily_target should have produced a non-empty reply").isNotBlank();
    }

    @Test
    void routesFastingQuestionToGetFastingStatusTool() {
        chatService.reply("I ate a chicken sandwich, 450 calories, 30g protein, 40g carbs, 15g fat");

        var reply = chatService.reply("how long has it been since I last ate?");

        assertThat(reply).as("get_fasting_status should have produced a non-empty reply").isNotBlank();
    }

    @Test
    void routesGoalWeightQuestionToGetWeightProjectionTool() {
        chatService.reply("weight this morning was 200 lbs");

        var reply = chatService.reply("at my current rate, when will I reach 190 lbs?");

        assertThat(reply).as("get_weight_projection should have produced a non-empty reply").isNotBlank();
    }

    @Test
    void routesUpcUtteranceToLogFoodByUpcToolAndCachesFoodItem() {
        // Coca-Cola 330ml can - a stable, well-populated product on Open Food Facts.
        chatService.reply("I scanned a UPC 5449000000996, I had one can");

        assertThat(foodEntryRepository.findAll())
                .as("log_food_by_upc should have looked up the product and persisted a FoodEntry")
                .hasSize(1);
        assertThat(foodItemRepository.findByUpc("5449000000996"))
                .as("log_food_by_upc should have cached the product as a FoodItem")
                .isPresent();
    }

    @Test
    void routesRepeatedNamedUtteranceToLogCachedFoodTool() {
        chatService.reply("I scanned a UPC 5449000000996, I had one can");
        assertThat(foodEntryRepository.findAll()).hasSize(1);

        chatService.reply("I had another Coca-Cola");

        assertThat(foodEntryRepository.findAll())
                .as("log_cached_food should have reused the cached FoodItem for a second entry")
                .hasSize(2);
    }

    @Test
    void routesShoppingRequestToAddShoppingItemTool() {
        chatService.reply("add almond milk to my shopping list");

        assertThat(shoppingItemRepository.findAll())
                .as("add_shopping_item should have been invoked and persisted a ShoppingItem")
                .hasSize(1);
    }

    @Test
    void routesPurchaseConfirmationToMarkShoppingItemPurchasedTool() {
        chatService.reply("add almond milk to my shopping list");
        assertThat(shoppingItemRepository.findAll()).hasSize(1);

        chatService.reply("I bought the almond milk at Trader Joe's");

        var items = shoppingItemRepository.findAll();
        assertThat(items).hasSize(1);
        assertThat(items.iterator().next().status())
                .as("mark_shopping_item_purchased should have updated the item's status in place")
                .isEqualTo("PURCHASED");
    }

    @Test
    void routesChartRequestToShowChartTool() {
        chatService.reply("weight this morning was 200 lbs");
        chatService.reply("I ate a chicken sandwich, 450 calories, 30g protein, 40g carbs, 15g fat");

        var reply = chatService.reply("show me a chart of my calories this week");

        assertThat(reply).as("show_chart should have produced a non-empty reply").isNotBlank();
    }

    @Test
    void routesPastDayFoodQuestionToListFoodEntriesTool() {
        chatService.reply("I ate a chicken sandwich, 450 calories, 30g protein, 40g carbs, 15g fat");

        var reply = chatService.reply("what did I eat today?");

        assertThat(reply).as("list_food_entries should have produced a non-empty reply").isNotBlank();
        assertThat(reply).as("reply should mention the logged food, not claim nothing was found")
                .containsIgnoringCase("chicken");
    }

    @Test
    void routesAnalyticalQuestionToRunSqlTool() {
        chatService.reply("I ate a chicken sandwich, 450 calories, 30g protein, 40g carbs, 15g fat");

        var reply = chatService.reply("how many times have I logged food this week?");

        assertThat(reply).as("run_sql should have produced a non-empty reply").isNotBlank();
        assertThat(savedQueryRepository.findAll())
                .as("run_sql should have saved the composed query for reuse")
                .isNotEmpty();
    }
}
