package com.chatdiet.intent;

import com.chatdiet.chat.ChatService;
import com.chatdiet.digestive.DigestiveEventRepository;
import com.chatdiet.exercise.ExerciseEntryRepository;
import com.chatdiet.food.FoodEntryRepository;
import com.chatdiet.fooditem.FoodItemRepository;
import com.chatdiet.note.NoteRepository;
import com.chatdiet.nutrition.DailyTargetRepository;
import com.chatdiet.requirement.RequirementEntryRepository;
import com.chatdiet.vitals.VitalsEntryRepository;
import com.chatdiet.weight.WeightEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

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
        registry.add("spring.datasource.url", () -> "jdbc:h2:file:" + tempDir.resolve("routing-test"));
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
    private RequirementEntryRepository requirementEntryRepository;

    @Autowired
    private DailyTargetRepository dailyTargetRepository;

    @Autowired
    private FoodItemRepository foodItemRepository;

    @BeforeEach
    void clearAll() {
        noteRepository.deleteAll();
        foodEntryRepository.deleteAll();
        weightEntryRepository.deleteAll();
        vitalsEntryRepository.deleteAll();
        exerciseEntryRepository.deleteAll();
        digestiveEventRepository.deleteAll();
        requirementEntryRepository.deleteAll();
        dailyTargetRepository.deleteAll();
        foodItemRepository.deleteAll();
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
    void routesFeatureRequestToSaveRequirementTool() {
        chatService.reply("it would be great if the app could remind me to log weight every morning");

        assertThat(requirementEntryRepository.findAll())
                .as("save_requirement should have been invoked and persisted a RequirementEntry")
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
    void routesCalorieTargetQuestionToGetDailyTargetTool() {
        chatService.reply("weight this morning was 200 lbs");
        chatService.reply("I ate a chicken sandwich, 450 calories, 30g protein, 40g carbs, 15g fat");

        chatService.reply("what's my calorie target for today and how many calories do I have left?");

        assertThat(dailyTargetRepository.findAll())
                .as("get_daily_target should have been invoked and computed/persisted a DailyTarget")
                .hasSize(1);
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
}
